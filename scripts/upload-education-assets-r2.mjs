#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const env = process.env;

if (process.argv.includes("--help") || process.argv.includes("-h")) {
  console.log(`Usage:
  EDUCATION_ASSET_SOURCE_ROOT=/path/to/KMP_DEV_HANDOFF_LIGHT_20260509 \\
  EDUCATION_ASSET_BUCKET=<r2-bucket> \\
  EDUCATION_ASSET_ENDPOINT_URL=https://<account-id>.r2.cloudflarestorage.com \\
  EDUCATION_ASSET_URL_STYLE=virtual-host \\
  AWS_ACCESS_KEY_ID=<key> AWS_SECRET_ACCESS_KEY=<secret> \\
  node scripts/upload-education-assets-r2.mjs

Required:
  EDUCATION_ASSET_SOURCE_ROOT
  EDUCATION_ASSET_BUCKET, R2_BUCKET, or CLOUDFLARE_R2_BUCKET
  EDUCATION_ASSET_ENDPOINT_URL, R2_ENDPOINT_URL, or CLOUDFLARE_R2_ENDPOINT_URL
  AWS_ACCESS_KEY_ID, EDUCATION_ASSET_ACCESS_KEY_ID, R2_ACCESS_KEY_ID, or CLOUDFLARE_R2_ACCESS_KEY_ID unless EDUCATION_ASSET_DRY_RUN=1
  AWS_SECRET_ACCESS_KEY, EDUCATION_ASSET_SECRET_ACCESS_KEY, R2_SECRET_ACCESS_KEY, or CLOUDFLARE_R2_SECRET_ACCESS_KEY unless EDUCATION_ASSET_DRY_RUN=1

Optional:
  EDUCATION_ASSET_UPLOAD_MANIFEST=src/main/resources/education/backend_asset_upload_manifest.json
  EDUCATION_ASSET_PUBLIC_BASE_URL or UNIPORT_EDU_ASSET_BASE_URL
  EDUCATION_ASSET_CACHE_CONTROL="public, max-age=31536000, immutable"
  EDUCATION_ASSET_URL_STYLE=path or virtual-host
  EDUCATION_ASSET_DRY_RUN=1
  EDUCATION_ASSET_VERIFY_REMOTE=1
`);
  process.exit(0);
}

const manifestPath = env.EDUCATION_ASSET_UPLOAD_MANIFEST
  || "src/main/resources/education/backend_asset_upload_manifest.json";
const sourceRoot = requireEnv("EDUCATION_ASSET_SOURCE_ROOT");
const bucket = firstEnv("EDUCATION_ASSET_BUCKET", "R2_BUCKET", "CLOUDFLARE_R2_BUCKET");
const endpointUrl = firstEnv(
  "EDUCATION_ASSET_ENDPOINT_URL",
  "R2_ENDPOINT_URL",
  "CLOUDFLARE_R2_ENDPOINT_URL",
);
const dryRun = env.EDUCATION_ASSET_DRY_RUN === "1";
const accessKeyId = dryRun ? "" : firstEnv(
  "AWS_ACCESS_KEY_ID",
  "EDUCATION_ASSET_ACCESS_KEY_ID",
  "R2_ACCESS_KEY_ID",
  "CLOUDFLARE_R2_ACCESS_KEY_ID",
);
const secretAccessKey = dryRun ? "" : firstEnv(
  "AWS_SECRET_ACCESS_KEY",
  "EDUCATION_ASSET_SECRET_ACCESS_KEY",
  "R2_SECRET_ACCESS_KEY",
  "CLOUDFLARE_R2_SECRET_ACCESS_KEY",
);
const sessionToken = firstOptionalEnv("AWS_SESSION_TOKEN", "EDUCATION_ASSET_SESSION_TOKEN");
const cacheControl = env.EDUCATION_ASSET_CACHE_CONTROL || "public, max-age=31536000, immutable";
const verifyRemote = env.EDUCATION_ASSET_VERIFY_REMOTE === "1";
const urlStyle = (env.EDUCATION_ASSET_URL_STYLE || env.R2_URL_STYLE || "path").trim();

if (!fs.existsSync(manifestPath)) {
  fail(`Manifest not found: ${manifestPath}`);
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const expectedCount = Number(manifest.total_assets);
const manifestAssets = Array.isArray(manifest.assets) ? manifest.assets : [];
if (expectedCount !== manifestAssets.length) {
  fail(`Asset count mismatch: total_assets=${expectedCount} assets.length=${manifestAssets.length}`);
}

const publicBaseUrl = (env.EDUCATION_ASSET_PUBLIC_BASE_URL || env.UNIPORT_EDU_ASSET_BASE_URL || manifest.asset_base_url || "")
  .replace(/\/+$/, "");
const endpoint = normalizeEndpoint(endpointUrl);
const uniqueAssets = dedupeAssets(manifestAssets);

console.log(`Education R2 upload: manifest_entries=${manifestAssets.length} unique_objects=${uniqueAssets.length} bucket=${bucket}`);
if (publicBaseUrl) {
  console.log(`Education R2 public base: ${publicBaseUrl}`);
}
if (dryRun) {
  console.log("Dry run enabled; no objects will be uploaded.");
}

let uploaded = 0;
let missing = 0;
for (const asset of uniqueAssets) {
  const localPath = asset.local_source_path;
  const publicPath = asset.target_public_path;
  const contentType = asset.content_type || "application/octet-stream";
  const sourcePath = path.join(sourceRoot, localPath);
  const objectKey = publicPath.replace(/^\/+/, "");

  if (!fs.existsSync(sourcePath)) {
    console.error(`Missing asset: ${sourcePath}`);
    missing += 1;
    continue;
  }

  if (dryRun) {
    console.log(`[dry-run] PUT ${objectKey} (${contentType})`);
    uploaded += 1;
    continue;
  }

  const body = fs.readFileSync(sourcePath);
  const targetUrl = objectUrlFor(endpoint, bucket, objectKey, urlStyle);
  const headers = signedPutHeaders({
    accessKeyId,
    secretAccessKey,
    sessionToken,
    url: targetUrl,
    body,
    contentType,
    cacheControl,
  });

  const response = await fetch(targetUrl, {
    method: "PUT",
    headers,
    body,
  });

  if (!response.ok) {
    const text = await response.text();
    fail(`Upload failed for ${objectKey}: HTTP ${response.status} ${response.statusText}\n${text}`);
  }
  uploaded += 1;
}

if (missing > 0) {
  fail(`Upload failed: ${missing} assets were missing.`);
}

if (verifyRemote) {
  if (!publicBaseUrl) {
    fail("EDUCATION_ASSET_VERIFY_REMOTE=1 requires EDUCATION_ASSET_PUBLIC_BASE_URL or UNIPORT_EDU_ASSET_BASE_URL.");
  }
  for (const asset of uniqueAssets) {
    const url = publicUrlFor(publicBaseUrl, asset.target_public_path);
    const response = await fetch(url, { method: "HEAD" });
    if (!response.ok) {
      fail(`Remote verification failed for ${url}: HTTP ${response.status} ${response.statusText}`);
    }
  }
}

console.log(`Education R2 upload complete: ${uploaded} unique objects processed.`);

function requireEnv(name) {
  const value = env[name];
  if (!value || value.trim() === "") {
    fail(`${name} is required.`);
  }
  return value.trim();
}

function firstEnv(...names) {
  const value = firstOptionalEnv(...names);
  if (!value) {
    fail(`${names.join(" or ")} is required.`);
  }
  return value;
}

function firstOptionalEnv(...names) {
  for (const name of names) {
    const value = env[name];
    if (value && value.trim() !== "") {
      return value.trim();
    }
  }
  return "";
}

function dedupeAssets(assets) {
  const byPath = new Map();
  for (const asset of assets) {
    const key = asset.target_public_path;
    if (!key) {
      fail("Every asset requires target_public_path.");
    }
    const existing = byPath.get(key);
    if (existing) {
      if (existing.local_source_path !== asset.local_source_path || existing.content_type !== asset.content_type) {
        fail(`Conflicting asset entries for ${key}`);
      }
      continue;
    }
    byPath.set(key, asset);
  }
  return [...byPath.values()];
}

function normalizeEndpoint(value) {
  const endpoint = value.replace(/\/+$/, "");
  const parsed = new URL(endpoint);
  if (parsed.protocol !== "https:") {
    fail("R2 endpoint URL must use https.");
  }
  return endpoint;
}

function encodeS3Path(value) {
  return value
    .split("/")
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment).replace(/[!'()*]/g, (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`))
    .join("/");
}

function objectUrlFor(endpoint, bucket, objectKey, style) {
  const parsedEndpoint = new URL(endpoint);
  if (style === "virtual-host") {
    return new URL(`${parsedEndpoint.protocol}//${bucket}.${parsedEndpoint.host}/${encodeS3Path(objectKey)}`);
  }
  if (style !== "path") {
    fail(`Unsupported EDUCATION_ASSET_URL_STYLE: ${style}`);
  }
  return new URL(`${endpoint}/${encodeS3Path(bucket)}/${encodeS3Path(objectKey)}`);
}

function signedPutHeaders({ accessKeyId, secretAccessKey, sessionToken, url, body, contentType, cacheControl }) {
  const now = new Date();
  const amzDate = toAmzDate(now);
  const dateStamp = amzDate.slice(0, 8);
  const payloadHash = sha256Hex(body);
  const credentialScope = `${dateStamp}/auto/s3/aws4_request`;
  const headers = {
    "cache-control": cacheControl,
    "content-type": contentType,
    "host": url.host,
    "x-amz-content-sha256": payloadHash,
    "x-amz-date": amzDate,
  };
  if (sessionToken) {
    headers["x-amz-security-token"] = sessionToken;
  }

  const sortedHeaderNames = Object.keys(headers).sort();
  const canonicalHeaders = sortedHeaderNames
    .map((name) => `${name}:${normalizeHeaderValue(headers[name])}\n`)
    .join("");
  const signedHeaders = sortedHeaderNames.join(";");
  const canonicalRequest = [
    "PUT",
    url.pathname,
    "",
    canonicalHeaders,
    signedHeaders,
    payloadHash,
  ].join("\n");
  const stringToSign = [
    "AWS4-HMAC-SHA256",
    amzDate,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join("\n");
  const signingKey = getSignatureKey(secretAccessKey, dateStamp, "auto", "s3");
  const signature = hmacHex(signingKey, stringToSign);

  headers.authorization = `AWS4-HMAC-SHA256 Credential=${accessKeyId}/${credentialScope}, SignedHeaders=${signedHeaders}, Signature=${signature}`;
  return headers;
}

function publicUrlFor(publicBaseUrl, publicPath) {
  const pathPart = publicPath.startsWith("/education-assets/")
    ? publicPath.substring("/education-assets".length)
    : publicPath.startsWith("/")
      ? publicPath
      : `/${publicPath}`;
  return `${publicBaseUrl}${pathPart}`;
}

function toAmzDate(date) {
  return date.toISOString().replace(/[:-]|\.\d{3}/g, "");
}

function normalizeHeaderValue(value) {
  return String(value).trim().replace(/\s+/g, " ");
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function hmac(key, value) {
  return crypto.createHmac("sha256", key).update(value).digest();
}

function hmacHex(key, value) {
  return crypto.createHmac("sha256", key).update(value).digest("hex");
}

function getSignatureKey(key, dateStamp, regionName, serviceName) {
  const dateKey = hmac(`AWS4${key}`, dateStamp);
  const dateRegionKey = hmac(dateKey, regionName);
  const dateRegionServiceKey = hmac(dateRegionKey, serviceName);
  return hmac(dateRegionServiceKey, "aws4_request");
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

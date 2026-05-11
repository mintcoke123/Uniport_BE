import os
from functools import lru_cache

from fastapi import FastAPI
from pydantic import BaseModel, Field
from transformers import pipeline


MODEL_NAME = os.getenv("FINBERT_MODEL_NAME", "snunlp/KR-FinBert-SC")

app = FastAPI(title="UniPort FinBERT Sentiment Service")


class AnalyzeRequest(BaseModel):
    newsId: str | None = None
    text: str = Field(min_length=1)


class AnalyzeResponse(BaseModel):
    label: str
    score: float
    model: str


@lru_cache(maxsize=1)
def classifier():
    return pipeline("text-classification", model=MODEL_NAME, tokenizer=MODEL_NAME)


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest):
    result = classifier()(request.text[:1500], truncation=True)[0]
    return AnalyzeResponse(
        label=str(result["label"]),
        score=float(result["score"]),
        model=MODEL_NAME,
    )

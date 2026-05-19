package com.uniport.repository;

import com.uniport.entity.TeamGameSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface TeamGameSnapshotRepository extends JpaRepository<TeamGameSnapshot, Long> {

    @Query("select max(s.snapshotAt) from TeamGameSnapshot s")
    Instant findLatestSnapshotAt();

    List<TeamGameSnapshot> findTop100BySnapshotAtOrderByReturnRateDescTotalAssetAmountDescTeamIdAsc(Instant snapshotAt);

    @Query("select s from TeamGameSnapshot s where s.snapshotDate = :snapshotDate order by s.returnRate desc, s.totalAssetAmount desc, s.teamId asc")
    List<TeamGameSnapshot> findBySnapshotDateForRanking(@Param("snapshotDate") LocalDate snapshotDate);
}

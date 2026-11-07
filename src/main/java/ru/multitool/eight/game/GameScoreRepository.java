package ru.multitool.eight.game;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {

    List<GameScore> findAllByOrderByScoreDescCreatedAtAsc(Pageable pageable);

    List<GameScore> findByLevelOrderByScoreDesc(int level, Pageable pageable);

    long countByLevel(int level);
}

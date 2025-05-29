package com.example.aascapibitrix24.repository;

import com.example.aascapibitrix24.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepository extends JpaRepository<TokenEntity, Long> {
    TokenEntity findTopByOrderByCreatedAtDesc();
}

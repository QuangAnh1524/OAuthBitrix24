package com.example.aascapibitrix24.repository;

import com.example.aascapibitrix24.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRepository extends JpaRepository<TokenEntity, Long> {
    
}

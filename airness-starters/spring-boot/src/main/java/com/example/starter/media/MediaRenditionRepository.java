package com.example.starter.media;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The renditions as the database holds them, one row per bytes and purpose.
 */
public interface MediaRenditionRepository extends JpaRepository<MediaRendition, RenditionKey> {
}

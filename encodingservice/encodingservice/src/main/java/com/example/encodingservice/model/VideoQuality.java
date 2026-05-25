package com.example.encodingservice.model;

/**
 * Represents one rung in the adaptive bitrate ladder.
 * Replaces the fragile int[] that required remembering index order.
 */
public record VideoQuality(int width, int height, int bitrateKbps) {
}
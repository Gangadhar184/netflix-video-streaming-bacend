package com.example.contentservice.model;

/*
  * Tracks the video processing lifecycle
  *
  * flow:
  * pending -> uploaded -> encoding -> encoded -> ready
  *                                 -> Failed
 */
public enum VideoStatus {
    PENDING, //movie added but not uploaded yet
    UPLOADED, // raw video uploaded to s3
    ENCODING, // ffmpeg is encoding the vidoe
    ENCODED,  // encoding complete
    READY,    // HLS playlist ready -> can be streamed
    FAILED    // encoding failed
}

#!/usr/bin/env bash

native-image \
  --no-fallback \
  --enable-http \
  --enable-https \
  --initialize-at-build-time \
  --report-unsupported-elements-at-runtime \
  -jar target/scala-3.8.1/planning-poker-app.jar \
  yappa
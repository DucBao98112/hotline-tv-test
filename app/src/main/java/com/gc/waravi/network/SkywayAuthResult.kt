package com.gc.waravi.network

sealed class SkywayAuthResult {
    data class SkywayAuthSuccessResult(val token: String) : SkywayAuthResult()
    data class SkywayAuthFailureResult(val error: String) : SkywayAuthResult()
}
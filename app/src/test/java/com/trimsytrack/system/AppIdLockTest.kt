package com.trimsytrack.system

import com.trimsytrack.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdLockTest {
    @Test
    fun appIdIsLockedToTrimsyTrack() {
        assertEquals("trimsytrack", BuildConfig.APP_ID)
    }
}

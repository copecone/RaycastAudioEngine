package io.github.copecone.raycastaudioengine

import kotlinx.cinterop.*
import openal.* // cinterop 에서 생성된 패키지 임포트
import platform.posix.usleep
import platform.windows.Sleep
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.PI // 사인파 생성에 필요
import kotlin.math.sin // 사인파 생성에 필요

// --- OpenAL 에러 체크 유틸리티 함수 (선택 사항이지만 디버깅에 유용) ---
@OptIn(ExperimentalForeignApi::class)
fun checkAlError(operation: String) {
    val error = alGetError()
    if (error != AL_NO_ERROR) {
        val errorStr = when (error) {
            AL_INVALID_NAME -> "AL_INVALID_NAME"
            AL_INVALID_ENUM -> "AL_INVALID_ENUM"
            AL_INVALID_VALUE -> "AL_INVALID_VALUE"
            AL_INVALID_OPERATION -> "AL_INVALID_OPERATION"
            AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY"
            else -> "Unknown OpenAL Error"
        }
        println(">> OpenAL Error after '$operation': $errorStr ($error)")
    }
}


// @Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE") // 이 Suppress는 필요 없을 수 있습니다.
@Suppress("MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE")
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun main() {
    println("OpenAL Buzzer Example (Based on User Code)")

    memScoped { // C 메모리 관리를 위한 스코프
        // --- 1. OpenAL 초기화 (사용자 코드 유지) ---
        val device = alcOpenDevice(null)
        if (device == null) {
            println("오디오 장치를 열 수 없습니다.")
            return@memScoped // memScoped 람다에서 반환
        }
        println("오디오 장치 열기 성공")

        val context = alcCreateContext(device, null)
        if (context == null) {
            println("OpenAL 컨텍스트를 생성할 수 없습니다.")
            alcCloseDevice(device)
            return@memScoped
        }
        println("OpenAL 컨텍스트 생성 성공")

        if (alcMakeContextCurrent(context) == ALC_FALSE.toByte()) { // 타입 변환 적용됨
            println("OpenAL 컨텍스트를 활성화할 수 없습니다.")
            alcDestroyContext(context)
            alcCloseDevice(device)
            return@memScoped
        }
        println("OpenAL 컨텍스트 활성화 성공")
        checkAlError("Context Activation") // 에러 체크 추가

        // --- 2. 소스 및 버퍼 생성 (사용자 코드 유지) ---
        val source = alloc<ALuintVar>()
        alGenSources(1, source.ptr)
        checkAlError("alGenSources")
        val sourceId = source.value
        println("소스 생성 성공: ID=$sourceId")

        val buffer = alloc<ALuintVar>()
        alGenBuffers(1, buffer.ptr)
        checkAlError("alGenBuffers")
        val bufferId = buffer.value
        println("버퍼 생성 성공: ID=$bufferId")

        // --- 3. 버저음(사인파) 데이터 생성 (신규 추가) ---
        val sampleRate = 44100
        val frequency = 880.0f // 버저 주파수 (Hz)
        val duration = 0.5f    // 소리 길이 (초)
        val numSamples = (sampleRate * duration).toInt()
        val amplitude = Short.MAX_VALUE * 0.8 // 최대 진폭의 80%

        val pcmData = ShortArray(numSamples) { i ->
            val time = i.toFloat() / sampleRate
            val value = amplitude * sin(2.0 * PI * frequency * time)
            value.toInt().toShort()
        }
        println("PCM 데이터 생성 완료: ${numSamples} samples")

        // --- 4. 생성된 데이터를 OpenAL 버퍼에 업로드 (신규 추가) ---
        pcmData.usePinned { pinnedData ->
            val dataPtr = pinnedData.addressOf(0)
            val dataSize = pcmData.size * Short.SIZE_BYTES // Short는 2바이트
            alBufferData(bufferId, AL_FORMAT_MONO16, dataPtr, dataSize, sampleRate)
            checkAlError("alBufferData (MONO16)")
        }
        println("버퍼에 PCM 데이터 업로드 완료")

        // --- 5. 소스 속성 설정 (사용자 코드 유지) ---
        alSourcef(sourceId, AL_PITCH, 1.0f)
        alSourcef(sourceId, AL_GAIN, 1.0f)
        alSource3f(sourceId, AL_POSITION, 0.0f, 0.0f, 0.0f)
        alSource3f(sourceId, AL_VELOCITY, 0.0f, 0.0f, 0.0f)
        alSourcei(sourceId, AL_LOOPING, AL_FALSE)
        checkAlError("alSource Properties Set")

        // --- 6. 소스에 버퍼 연결 (신규 추가) ---
        alSourcei(sourceId, AL_BUFFER, bufferId.toInt()) // bufferId는 ALuint(UInt)이므로 Int로 변환 필요할 수 있음
        checkAlError("alSourcei AL_BUFFER Attach")
        println("소스에 버퍼 연결 완료")

        // --- 7. 소리 재생 (신규 추가) ---
        alSourcePlay(sourceId)
        checkAlError("alSourcePlay")
        println("버저음 재생 시작!")

        // --- 8. 재생이 끝날 때까지 대기 (신규 추가) ---
        val sourceState = alloc<ALintVar>()
        val delayMillis = 10L // 10 밀리초 대기
        println("재생 대기 시작 (Platform Sleep)...")

        do {
            alGetSourcei(sourceId, AL_SOURCE_STATE, sourceState.ptr)
            checkAlError("alGetSourcei AL_SOURCE_STATE") // 에러 체크 복원

            // 상태가 여전히 재생 중일 때만 sleep 호출
            if (sourceState.value == AL_PLAYING) {
                // 현재 OS에 맞는 sleep 함수 호출
                when (Platform.osFamily) {
                    // Linux, macOS 등 POSIX 호환 시스템
                    OsFamily.LINUX, OsFamily.MACOSX, OsFamily.IOS, OsFamily.TVOS, OsFamily.WATCHOS ->
                        usleep((delayMillis * 1000).toUInt()) // usleep은 마이크로초 단위

                    // Windows 시스템
                    OsFamily.WINDOWS ->
                        Sleep(delayMillis.toUInt()) // Sleep은 밀리초 단위

                    // 기타 지원되지 않는 시스템 (예: WASM, Android Native 등)
                    else -> {
                        // 이 경우 Busy-Wait 또는 다른 대기 메커니즘 사용
                        // 여기서는 간단히 아무것도 안 함 (사실상 Busy-Wait)
                    }
                }
            }
        } while (sourceState.value == AL_PLAYING)
        println("버저음 재생 완료.")

        // --- 9. 정리 (사용자 코드 순서 조정 및 보강) ---
        // 재생 완료 후 정리 시작
        println("정리 시작...")

        alSourceStop(sourceId) // 재생 중일 수 있으니 정지
        checkAlError("alSourceStop")
        alSourcei(sourceId, AL_BUFFER, 0) // 소스에서 버퍼 분리 (0은 NULL 버퍼를 의미)
        checkAlError("alSourcei AL_BUFFER Detach")

        // 소스와 버퍼 삭제 (ID를 저장한 변수 사용)
        alDeleteSources(1, source.ptr)
        checkAlError("alDeleteSources")
        alDeleteBuffers(1, buffer.ptr)
        checkAlError("alDeleteBuffers")
        println("소스 및 버퍼 삭제 완료")

        // 컨텍스트 및 장치 정리 (사용자 코드 유지)
        alcMakeContextCurrent(null)
        alcDestroyContext(context)
        alcCloseDevice(device)
        println("컨텍스트 및 장치 정리 완료")
    } // memScoped 종료
    println("프로그램 정상 종료")
} // main 종료
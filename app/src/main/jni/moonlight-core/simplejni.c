#include <RtRemote.h>

#include <jni.h>
#include <android/log.h>

#include <arpa/inet.h>
#include <string.h>

#include "minisdl.h"
#include "controller_type.h"
#include "controller_list.h"

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMouseMove(JNIEnv *env, jclass clazz, jshort deltaX, jshort deltaY) {
    RtSendMouseMoveEvent(deltaX, deltaY);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMousePosition(JNIEnv *env, jclass clazz,
        jshort x, jshort y, jshort referenceWidth, jshort referenceHeight) {
    RtSendMousePositionEvent(x, y, referenceWidth, referenceHeight);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMouseMoveAsMousePosition(JNIEnv *env, jclass clazz,
        jshort deltaX, jshort deltaY, jshort referenceWidth, jshort referenceHeight) {
    RtSendMouseMoveAsMousePositionEvent(deltaX, deltaY, referenceWidth, referenceHeight);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMouseButton(JNIEnv *env, jclass clazz, jbyte buttonEvent, jbyte mouseButton) {
    RtSendMouseButtonEvent(buttonEvent, mouseButton);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMultiControllerInput(JNIEnv *env, jclass clazz, jshort controllerNumber,
                                                           jshort activeGamepadMask, jint buttonFlags,
                                                           jbyte leftTrigger, jbyte rightTrigger,
                                                           jshort leftStickX, jshort leftStickY,
                                                           jshort rightStickX, jshort rightStickY) {
    RtSendMultiControllerEvent(controllerNumber, activeGamepadMask, buttonFlags,
        leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendTouchEvent(JNIEnv *env, jclass clazz,
                                                          jbyte eventType, jint pointerId,
                                                          jfloat x, jfloat y, jfloat pressureOrDistance,
                                                          jfloat contactAreaMajor, jfloat contactAreaMinor,
                                                          jshort rotation) {
    return RtSendTouchEvent(eventType, pointerId, x, y, pressureOrDistance,
                            contactAreaMajor, contactAreaMinor, rotation);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendPenEvent(JNIEnv *env, jclass clazz, jbyte eventType,
                                                        jbyte toolType, jbyte penButtons,
                                                        jfloat x, jfloat y, jfloat pressureOrDistance,
                                                        jfloat contactAreaMajor, jfloat contactAreaMinor,
                                                        jshort rotation, jbyte tilt) {
    return RtSendPenEvent(eventType, toolType, penButtons, x, y, pressureOrDistance,
                          contactAreaMajor, contactAreaMinor, rotation, tilt);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendControllerArrivalEvent(JNIEnv *env, jclass clazz,
                                                                      jbyte controllerNumber,
                                                                      jshort activeGamepadMask,
                                                                      jbyte type,
                                                                      jint supportedButtonFlags,
                                                                      jshort capabilities) {
    return RtSendControllerArrivalEvent(controllerNumber, activeGamepadMask, type, supportedButtonFlags, capabilities);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendControllerTouchEvent(JNIEnv *env, jclass clazz,
                                                                    jbyte controllerNumber,
                                                                    jbyte eventType,
                                                                    jint pointerId, jfloat x,
                                                                    jfloat y, jfloat pressure) {
    return RtSendControllerTouchEvent(controllerNumber, eventType, pointerId, x, y, pressure);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendControllerMotionEvent(JNIEnv *env, jclass clazz,
                                                                     jbyte controllerNumber,
                                                                     jbyte motionType, jfloat x,
                                                                     jfloat y, jfloat z) {
    return RtSendControllerMotionEvent(controllerNumber, motionType, x, y, z);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendControllerBatteryEvent(JNIEnv *env, jclass clazz,
                                                                      jbyte controllerNumber,
                                                                      jbyte batteryState,
                                                                      jbyte batteryPercentage) {
    return RtSendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendKeyboardInput(JNIEnv *env, jclass clazz, jshort keyCode, jbyte keyAction, jbyte modifiers, jbyte flags) {
    RtSendKeyboardEvent2(keyCode, keyAction, modifiers, flags);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMouseHighResScroll(JNIEnv *env, jclass clazz, jshort scrollAmount) {
    RtSendHighResScrollEvent(scrollAmount);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendMouseHighResHScroll(JNIEnv *env, jclass clazz, jshort scrollAmount) {
    RtSendHighResHScrollEvent(scrollAmount);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_sendUtf8Text(JNIEnv *env, jclass clazz, jstring text) {
    const char* utf8Text = (*env)->GetStringUTFChars(env, text, NULL);
    RtSendUtf8TextEvent(utf8Text, strlen(utf8Text));
    (*env)->ReleaseStringUTFChars(env, text, utf8Text);
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_stopConnection(JNIEnv *env, jclass clazz) {
    RtStopConnection();
}

JNIEXPORT void JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_interruptConnection(JNIEnv *env, jclass clazz) {
    RtInterruptConnection();
}

JNIEXPORT jstring JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getStageName(JNIEnv *env, jclass clazz, jint stage) {
    return (*env)->NewStringUTF(env, RtGetStageName(stage));
}

JNIEXPORT jstring JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_findExternalAddressIP4(JNIEnv *env, jclass clazz, jstring stunHostName, jint stunPort) {
    int err;
    struct in_addr wanAddr;
    const char* stunHostNameStr = (*env)->GetStringUTFChars(env, stunHostName, NULL);

    err = RtFindExternalAddressIP4(stunHostNameStr, stunPort, &wanAddr.s_addr);
    (*env)->ReleaseStringUTFChars(env, stunHostName, stunHostNameStr);

    if (err == 0) {
        char addrStr[INET_ADDRSTRLEN];

        inet_ntop(AF_INET, &wanAddr, addrStr, sizeof(addrStr));

        __android_log_print(ANDROID_LOG_INFO, "moonlight-common-c", "Resolved WAN address to %s", addrStr);

        return (*env)->NewStringUTF(env, addrStr);
    }
    else {
        __android_log_print(ANDROID_LOG_ERROR, "moonlight-common-c", "STUN failed to get WAN address: %d", err);
        return NULL;
    }
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getPendingAudioDuration(JNIEnv *env, jclass clazz) {
    return RtGetPendingAudioDuration();
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getPendingVideoFrames(JNIEnv *env, jclass clazz) {
    return RtGetPendingVideoFrames();
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_testClientConnectivity(JNIEnv *env, jclass clazz, jstring testServerHostName, jint referencePort, jint testFlags) {
    int ret;
    const char* testServerHostNameStr = (*env)->GetStringUTFChars(env, testServerHostName, NULL);

    ret = RtTestClientConnectivity(testServerHostNameStr, (unsigned short)referencePort, testFlags);

    (*env)->ReleaseStringUTFChars(env, testServerHostName, testServerHostNameStr);

    return ret;
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getPortFlagsFromStage(JNIEnv *env, jclass clazz, jint stage) {
    return RtGetPortFlagsFromStage(stage);
}

JNIEXPORT jint JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getPortFlagsFromTerminationErrorCode(JNIEnv *env, jclass clazz, jint errorCode) {
    return RtGetPortFlagsFromTerminationErrorCode(errorCode);
}

JNIEXPORT jstring JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_stringifyPortFlags(JNIEnv *env, jclass clazz, jint portFlags, jstring separator) {
    const char* separatorStr = (*env)->GetStringUTFChars(env, separator, NULL);
    char outputBuffer[512];

    RtStringifyPortFlags(portFlags, separatorStr, outputBuffer, sizeof(outputBuffer));

    (*env)->ReleaseStringUTFChars(env, separator, separatorStr);
    return (*env)->NewStringUTF(env, outputBuffer);
}

JNIEXPORT jlong JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getEstimatedRttInfo(JNIEnv *env, jclass clazz) {
    uint32_t rtt, variance;

    if (!RtGetEstimatedRttInfo(&rtt, &variance)) {
        return -1;
    }

    return ((uint64_t)rtt << 32U) | variance;
}

JNIEXPORT jstring JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_getLaunchUrlQueryParameters(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, RtGetLaunchUrlQueryParameters());
}

JNIEXPORT jbyte JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_guessControllerType(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    unsigned int unDeviceID = MAKE_CONTROLLER_ID(vendorId, productId);
    for (int i = 0; i < sizeof(arrControllers) / sizeof(arrControllers[0]); i++) {
        if (unDeviceID == arrControllers[i].m_unDeviceID) {
            switch (arrControllers[i].m_eControllerType) {
                case k_eControllerType_XBox360Controller:
                case k_eControllerType_XBoxOneController:
                    return RT_CTYPE_XBOX;

                case k_eControllerType_PS3Controller:
                case k_eControllerType_PS4Controller:
                case k_eControllerType_PS5Controller:
                    return RT_CTYPE_PS;

                case k_eControllerType_WiiController:
                case k_eControllerType_SwitchProController:
                case k_eControllerType_SwitchJoyConLeft:
                case k_eControllerType_SwitchJoyConRight:
                case k_eControllerType_SwitchJoyConPair:
                case k_eControllerType_SwitchInputOnlyController:
                    return RT_CTYPE_NINTENDO;

                default:
                    return RT_CTYPE_UNKNOWN;
            }
        }
    }
    return RT_CTYPE_UNKNOWN;
}

JNIEXPORT jboolean JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_guessControllerHasPaddles(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    // Xbox Elite and DualSense Edge controllers have paddles
    return SDL_IsJoystickXboxOneElite(vendorId, productId) || SDL_IsJoystickDualSenseEdge(vendorId, productId);
}

JNIEXPORT jboolean JNICALL
Java_kr_co_antsnest_rtremote_nvstream_jni_RtBridge_guessControllerHasShareButton(JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    // Xbox Elite and DualSense Edge controllers have paddles
    return SDL_IsJoystickXboxSeriesX(vendorId, productId);
}
#!/bin/bash

function prepare_environment_variables() {
    NDK=$1
    PREBUILT_PREFIX=$2
    PLATFORM=$3
    ADD_FLAGS=$4
    export CPP=$PREBUILT_PREFIX-cpp
    export CC=$PREBUILT_PREFIX-gcc
    export CXX=$PREBUILT_PREFIX-g++
    export LD=$PREBUILT_PREFIX-ld
    export AS=$PREBUILT_PREFIX-as
    export AR=$PREBUILT_PREFIX-ar
    export RANLIB=$PREBUILT_PREFIX-ranlib

    export CPPFLAGS="--sysroot=$PLATFORM $ADD_FLAGS"
    export CFLAGS="--sysroot=$PLATFORM $ADD_FLAGS"
    export CXXFLAGS="--sysroot=$PLATFORM $ADD_FLAGS"
}

function domake() {
    out_dir=$1
    host=$2
    $flac_path/configure --without-ogg --disable-cpplibs \
        --disable-doxygen-docs \
        --host=$HOST \
        --prefix=$curDir/output/common \
        --exec-prefix=$curDir/output/$out_dir \
        && make \
        && make install
}

NDK=/Users/zhuwenbo/Library/Android/sdk/ndk-bundle
flac_path='../flac-1.3.2'
curDir=`pwd`

#armeabi
function build_armeabi() {
    PLATFORM=$NDK/platforms/android-14/arch-arm
    PREBUILT_PREFIX=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/darwin-x86_64/bin/arm-linux-androideabi

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM '-march=armv5te'

    HOST=arm-linux-androideabi
    domake armeabi $HOST
}

#arm64
function build_arm64() {
    PLATFORM=$NDK/platforms/android-21/arch-arm64
    PREBUILT_PREFIX=$NDK/toolchains/aarch64-linux-android-4.9/prebuilt/darwin-x86_64/bin/aarch64-linux-android

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM

    HOST=aarch64-linux-android
    domake arm64-v8a $HOST
}

#x86
function build_x86() {
    PLATFORM=$NDK/platforms/android-14/arch-x86
    PREBUILT_PREFIX=$NDK/toolchains/x86-4.9/prebuilt/darwin-x86_64/bin/i686-linux-android

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM

    HOST=i686-linux-android
    domake x86 $HOST
}

#x86_64
function build_x86_64() {
    PLATFORM=$NDK/platforms/android-21/arch-x86_64
    PREBUILT_PREFIX=$NDK/toolchains/x86_64-4.9/prebuilt/darwin-x86_64/bin/x86_64-linux-android

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM

    HOST=x86_64-linux-android
    domake x86_64 $HOST
}

#mips
function build_mips() {
    PLATFORM=$NDK/platforms/android-14/arch-mips
    PREBUILT_PREFIX=$NDK/toolchains/mipsel-linux-android-4.9/prebuilt/darwin-x86_64/bin/mipsel-linux-android

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM

    HOST=mipsel-linux-android
    domake mips $HOST
}

#mips64
function build_mips64() {
    PLATFORM=$NDK/platforms/android-21/arch-mips64
    PREBUILT_PREFIX=$NDK/toolchains/mips64el-linux-android-4.9/prebuilt/darwin-x86_64/bin/mips64el-linux-android

    prepare_environment_variables $NDK $PREBUILT_PREFIX $PLATFORM

    HOST=mips64el-linux-android
    domake mips64 $HOST
}

build_armeabi
build_arm64
build_x86
build_x86_64
build_mips
build_mips64

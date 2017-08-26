# 一个录音并编码为flac文件的库

## 文件结构

分为三个部分。

### FLAC库

路径/flac-1.3.2是flac源码。见[FLAC](https://github.com/xiph/flac)。
路径/flac-build下是编译脚本，输出libFLAC.so和头文件。输出路径/flac-build/output。

执行/flac-build/build.sh进行编译。需要修改NDK库相关的路径。

### 录音库

路径/flaclib

封装类FlacRecorder用于录音。
用AudioRecord进行录音并用FLAC库编码输出到文件。

### Demo

路径/flacdemo

提供使用FlacRecorder进行录音的示例。


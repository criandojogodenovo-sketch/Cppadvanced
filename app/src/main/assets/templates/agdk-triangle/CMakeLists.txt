cmake_minimum_required(VERSION 3.22.1)
project({{LIB_NAME}} LANGUAGES C CXX)

# GameActivity (AGDK) via Prefab — inclui o native_app_glue nativo
find_package(game-activity REQUIRED CONFIG)

add_library({{LIB_NAME}} SHARED
    main.cpp)

target_link_libraries({{LIB_NAME}}
    PRIVATE
        game-activity::game-activity
        android
        log
        EGL
        GLESv3)

target_compile_features({{LIB_NAME}} PRIVATE cxx_std_17)

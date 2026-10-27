#version 300 es
// Triângulo OpenGL ES 3.0 — vertex shader (ficheiro separado, carregado dos assets)
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec3 aColor;

out vec3 vColor;

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vColor = aColor;
}

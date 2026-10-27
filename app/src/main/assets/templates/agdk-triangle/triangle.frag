#version 300 es
// Triângulo OpenGL ES 3.0 — fragment shader (ficheiro separado, carregado dos assets)
precision mediump float;

in vec3 vColor;
out vec4 fragColor;

void main() {
    fragColor = vec4(vColor, 1.0);
}

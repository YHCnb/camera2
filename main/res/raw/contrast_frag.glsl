precision mediump float;

varying vec2 aCoord;

uniform sampler2D vTexture;
uniform float contrast;

void main() {
    lowp vec4 textureColor = texture2D(vTexture, aCoord);
    gl_FragColor = vec4(((textureColor.rgb - vec3(0.5)) * contrast + vec3(0.5)), textureColor.w);
}


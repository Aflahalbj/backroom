#include veil:camera
#include veil:deferred_utils
#include veil:fog
#include spb-revamped:common
#include spb-revamped:shadows

uniform sampler2D DiffuseSampler0;
uniform sampler2D DepthSampler;
uniform sampler2D TransparentDepthSampler;
uniform sampler2D HandDepth;
uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

const int ITERATIONS = 125;
const float STEPSIZE = .2;
const vec4 fogColor = vec4(0.5, 0.5, 0.5, 0.5);


vec4 screenToWorldSpace(vec2 uv, float depth) {
    vec4 viewSpacePos = VeilCamera.IProjMat * (vec4(uv, depth, 1.0) * 2.0 - 1.0);
    return vec4(VeilCamera.CameraPosition, 0.0) + VeilCamera.IViewMat * (viewSpacePos / viewSpacePos.w);
}

void main() {
    vec4 baseColor = texture(DiffuseSampler0, texCoord);
    #ifdef VOLUMETRIC_LIGHT
        #ifdef LEVEL324
        float handDepth = texture(HandDepth, texCoord).r;
        float depth = min(handDepth, texture(DepthSampler, texCoord).r);


        vec3 viewSpace = viewPosFromDepth(depth, texCoord);
        vec3 worldSpace = viewToWorldSpace(viewSpace);

        vec3 playerPos = VeilCamera.CameraPosition;
        if (playerPos.y > 40) {
            vec3 depthPos = screenToWorldSpace(texCoord, depth).xyz;

            vec3 ditherDirection = viewDirFromUv(texCoord) + dither(texCoord, ScreenSize, 1) * 0.1;
            vec3 direction = viewDirFromUv(texCoord);

            vec3[9] lightPositions = vec3[9](
                vec3(10.5, 71.5, 13.5),
                vec3(10.5, 71.5, 21.5),
                vec3(22.5, 71.5, 5.5),
                vec3(10.5, 71.5, 5.5),
                vec3(22.5, 71.5, 13.5),
                vec3(22.5, 71.5, 21.5),

                vec3(31.5, 70.5, 23.5),
                vec3(31.5, 70.5, 13.5),
                vec3(31.5, 70.5, 3.5)
            );

            for (int i = 0; i < ITERATIONS; ++i) {
                vec3 ditherRaypos = playerPos + (ditherDirection * STEPSIZE * i);
                vec3 raypos = playerPos + (direction * STEPSIZE * i);

                if ((66 - raypos.y) / 2 > 0 && length(raypos.xz - vec2(13, 12)) < 20) {
                    baseColor += fogColor * ((66 - raypos.y) / 2) * .01;
                }

                for (int j = 0; j < lightPositions.length(); j++) {
                    vec3 lightPos = lightPositions[j];

                    if (lightPos.y > ditherRaypos.y && length(lightPos.xz - ditherRaypos.xz) < (lightPos.y - ditherRaypos.y) * .5) {
                        baseColor += (fogColor * .05) * (pow(.1,length(lightPos.xz - ditherRaypos.xz) / ((lightPos.y - ditherRaypos.y) * .5)) * .6);
                    }
                }

                if (length(raypos - playerPos) > length(depthPos - playerPos) || raypos.y < 64) {
                    break; // Stop if we have moved past the target position
                }
            }
        }
        #endif
    #endif


    fragColor = baseColor;
}
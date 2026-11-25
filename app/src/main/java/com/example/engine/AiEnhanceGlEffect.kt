package com.example.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

data class ShaderUniforms(
    val sharpen: Float = 0.6f,
    val denoise: Float = 0.35f,
    val hdrClarity: Float = 0.4f,
    val vibrance: Float = 0.25f,
    val splitPosition: Float = 0.5f,
    val showSplit: Boolean = false
)

class LiveEnhancementState {
    @Volatile var sharpen: Float = 0.62f
    @Volatile var denoise: Float = 0.35f
    @Volatile var hdrClarity: Float = 0.40f
    @Volatile var vibrance: Float = 0.25f
    @Volatile var splitPosition: Float = 0.50f
    @Volatile var showSplit: Boolean = true

    fun snapshot(): ShaderUniforms = ShaderUniforms(
        sharpen = sharpen,
        denoise = denoise,
        hdrClarity = hdrClarity,
        vibrance = vibrance,
        splitPosition = splitPosition,
        showSplit = showSplit
    )
}

@UnstableApi
class AiEnhanceGlEffect(
    private val uniformProvider: () -> ShaderUniforms
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHighPrecisionColorComponents: Boolean
    ): GlShaderProgram {
        return AiEnhanceShaderProgram(
            useHighPrecisionColorComponents = useHighPrecisionColorComponents,
            uniformProvider = uniformProvider
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

@UnstableApi
private class AiEnhanceShaderProgram(
    useHighPrecisionColorComponents: Boolean,
    private val uniformProvider: () -> ShaderUniforms
) : BaseGlShaderProgram(useHighPrecisionColorComponents, /* texturePoolCapacity= */ 1) {

    private var glProgram: GlProgram? = null
    private var width: Int = 1
    private var height: Int = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.coerceAtLeast(1)
        height = inputHeight.coerceAtLeast(1)
        if (glProgram == null) {
            try {
                glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                    setBufferAttribute(
                        "aFramePosition",
                        GlUtil.getNormalizedCoordinateBounds(),
                        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                    )
                }
            } catch (e: Exception) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(width, height)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: throw VideoFrameProcessingException("GlProgram not initialized")
        val u = uniformProvider()
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexelSize", floatArrayOf(1f / width.toFloat(), 1f / height.toFloat()))
            program.setFloatUniform("uSharpen", u.sharpen.coerceIn(0f, 1f))
            program.setFloatUniform("uDenoise", u.denoise.coerceIn(0f, 1f))
            program.setFloatUniform("uHdrClarity", u.hdrClarity.coerceIn(0f, 1f))
            program.setFloatUniform("uVibrance", u.vibrance.coerceIn(0f, 1f))
            program.setFloatUniform("uSplitX", u.splitPosition.coerceIn(0f, 1f))
            program.setFloatUniform("uShowSplit", if (u.showSplit) 1f else 0f)
            program.bindAttributesAndUniforms()

            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram?.delete()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        } finally {
            glProgram = null
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uSharpen;
            uniform float uDenoise;
            uniform float uHdrClarity;
            uniform float uVibrance;
            uniform float uSplitX;
            uniform float uShowSplit;
            varying vec2 vTexSamplingCoord;

            float luma(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec4 centerSample = texture2D(uTexSampler, uv);
                vec3 orig = centerSample.rgb;

                // Split-screen comparison: left of divider shows original raw frame
                if (uShowSplit > 0.5) {
                    float distToLine = abs(uv.x - uSplitX);
                    if (distToLine < uTexelSize.x * 2.2) {
                        gl_FragColor = vec4(0.0, 0.90, 1.0, centerSample.a);
                        return;
                    }
                    if (uv.x < uSplitX) {
                        gl_FragColor = centerSample;
                        return;
                    }
                }

                // 3x3 neighborhood sampling for bilateral denoise + edge-aware super-res sharpening
                vec2 dx = vec2(uTexelSize.x, 0.0);
                vec2 dy = vec2(0.0, uTexelSize.y);

                vec3 n  = texture2D(uTexSampler, uv + dy).rgb;
                vec3 s  = texture2D(uTexSampler, uv - dy).rgb;
                vec3 e  = texture2D(uTexSampler, uv + dx).rgb;
                vec3 w  = texture2D(uTexSampler, uv - dx).rgb;
                vec3 ne = texture2D(uTexSampler, uv + dx + dy).rgb;
                vec3 nw = texture2D(uTexSampler, uv - dx + dy).rgb;
                vec3 se = texture2D(uTexSampler, uv + dx - dy).rgb;
                vec3 sw = texture2D(uTexSampler, uv - dx - dy).rgb;

                // 1. Edge-preserving spatial bilateral denoise & macroblock smoothing
                float rangeSigma = mix(0.015, 0.14, uDenoise);
                float invTwoSigmaSq = 1.0 / (2.0 * rangeSigma * rangeSigma + 0.0001);

                vec3 bilatSum = orig;
                float weightSum = 1.0;

                vec3 d1 = n - orig; float w1 = exp(-dot(d1, d1) * invTwoSigmaSq) * 0.85;
                vec3 d2 = s - orig; float w2 = exp(-dot(d2, d2) * invTwoSigmaSq) * 0.85;
                vec3 d3 = e - orig; float w3 = exp(-dot(d3, d3) * invTwoSigmaSq) * 0.85;
                vec3 d4 = w - orig; float w4 = exp(-dot(d4, d4) * invTwoSigmaSq) * 0.85;
                vec3 d5 = ne - orig; float w5 = exp(-dot(d5, d5) * invTwoSigmaSq) * 0.55;
                vec3 d6 = nw - orig; float w6 = exp(-dot(d6, d6) * invTwoSigmaSq) * 0.55;
                vec3 d7 = se - orig; float w7 = exp(-dot(d7, d7) * invTwoSigmaSq) * 0.55;
                vec3 d8 = sw - orig; float w8 = exp(-dot(d8, d8) * invTwoSigmaSq) * 0.55;

                bilatSum += n * w1 + s * w2 + e * w3 + w * w4 + ne * w5 + nw * w6 + se * w7 + sw * w8;
                weightSum += w1 + w2 + w3 + w4 + w5 + w6 + w7 + w8;

                vec3 denoised = mix(orig, bilatSum / weightSum, uDenoise);

                // 2. Anti-halo adaptive unsharp super-resolution detail restoration
                vec3 blurAvg = (n + s + e + w) * 0.18 + (ne + nw + se + sw) * 0.07;
                vec3 highFreq = denoised - blurAvg;
                // Suppress amplification on flat noisy patches, emphasize crisp structure
                float edgeStrength = clamp(length(e - w) + length(n - s), 0.0, 1.0);
                float sharpGain = uSharpen * mix(0.55, 1.85, smoothstep(0.03, 0.25, edgeStrength));
                vec3 clampedDetail = clamp(highFreq * sharpGain, vec3(-0.22), vec3(0.22));
                vec3 sharpened = clamp(denoised + clampedDetail, 0.0, 1.0);

                // 3. Adaptive HDR tone mapping (shadow lift + local S-curve contrast)
                float lum = max(luma(sharpened), 0.0001);
                float shadowLift = pow(lum, mix(1.0, 0.82, uHdrClarity));
                // Smooth S-curve contrast
                float sCurve = shadowLift * shadowLift * (3.0 - 2.0 * shadowLift);
                float targetLum = mix(lum, mix(shadowLift, sCurve, 0.58), uHdrClarity * 0.85);
                vec3 hdr = clamp(sharpened * (targetLum / lum), 0.0, 1.0);

                // 4. Perceptual vibrance (boosts muted tones while protecting saturated regions)
                float maxC = max(hdr.r, max(hdr.g, hdr.b));
                float minC = min(hdr.r, min(hdr.g, hdr.b));
                float sat = maxC - minC;
                float vibranceWeight = uVibrance * 1.15 * (1.0 - sat);
                float gray = luma(hdr);
                vec3 finalRgb = clamp(mix(vec3(gray), hdr, 1.0 + vibranceWeight), 0.0, 1.0);

                gl_FragColor = vec4(finalRgb, centerSample.a);
            }
        """
    }
}

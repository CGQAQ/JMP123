package jmp123.output

class FFT {
    private val real: FloatArray
    private val imag: FloatArray
    private val sintable: FloatArray
    private val costable: FloatArray
    private val bitReverse: IntArray
    fun getModulus(realIO: FloatArray) {
        var j: Int
        var k: Int
        var ir: Int
        var j0 = 1
        var idx = FFT_N_LOG - 1
        var cosv: Float
        var sinv: Float
        var tmpr: Float
        var tmpi: Float
        var i: Int = 0
        while (i != FFT_N) {
            real[i] = realIO[bitReverse[i]]
            imag[i] = 0F
            i++
        }
        i = FFT_N_LOG
        while (i != 0) {
            j = 0
            while (j != j0) {
                cosv = costable[j shl idx]
                sinv = sintable[j shl idx]
                k = j
                while (k < FFT_N) {
                    ir = k + j0
                    tmpr = cosv * real[ir] - sinv * imag[ir]
                    tmpi = cosv * imag[ir] + sinv * real[ir]
                    real[ir] = real[k] - tmpr
                    imag[ir] = imag[k] - tmpi
                    real[k] += tmpr
                    imag[k] += tmpi
                    k += j0 shl 1
                }
                j++
            }
            j0 = j0 shl 1
            idx--
            i--
        }
        j = FFT_N shr 1
        /*
		 * 输出模的平方:
		 * for(i = 1; i <= j; i++)
		 * 	realIO[i-1] = real[i] * real[i] +  imag[i] * imag[i];
		 * 
		 * 如果FFT只用于频谱显示,可以"淘汰"幅值较小的而减少浮点乘法运算. MINY的值
		 * 和Spectrum.Y0,Spectrum.logY0对应.
		 */sinv = MINY
        cosv = -MINY
        i = j
        while (i != 0) {
            tmpr = real[i]
            tmpi = imag[i]
            if (tmpr > cosv && tmpr < sinv && tmpi > cosv && tmpi < sinv) realIO[i - 1] = 0F else realIO[i - 1] = tmpr * tmpr + tmpi * tmpi
            i--
        }
    }

    companion object {
        const val FFT_N_LOG = 9 // FFT_N_LOG <= 13
        const val FFT_N = 1 shl FFT_N_LOG
        private val MINY = ((FFT_N shl 2) * Math.sqrt(2.0)).toFloat() // (*)
    }

    init {
        real = FloatArray(FFT_N)
        imag = FloatArray(FFT_N)
        sintable = FloatArray(FFT_N shr 1)
        costable = FloatArray(FFT_N shr 1)
        bitReverse = IntArray(FFT_N)
        var j: Int
        var k: Int
        var reve: Int
        var i: Int = 0
        while (i < FFT_N) {
            k = i
            j = 0
            reve = 0
            while (j != FFT_N_LOG) {
                reve = reve shl 1
                reve = reve or (k and 1)
                k = k ushr 1
                j++
            }
            bitReverse[i] = reve
            i++
        }
        var theta: Double
        val dt = 2 * 3.14159265358979323846 / FFT_N
        i = (FFT_N shr 1) - 1
        while (i > 0) {
            theta = i * dt
            costable[i] = Math.cos(theta).toFloat()
            sintable[i] = Math.sin(theta).toFloat()
            i--
        }
    }
}
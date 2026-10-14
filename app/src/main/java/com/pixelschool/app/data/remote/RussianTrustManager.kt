package com.pixelschool.app.data.remote

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object RussianTrustManager {

    private const val RUSSIAN_TRUSTED_ROOT_CA = """-----BEGIN CERTIFICATE-----
MIIFwjCCA6qgAwIBAgICEAAwDQYJKoZIhvcNAQELBQAwcDELMAkGA1UEBhMCUlUx
PzA9BgNVBAoMNlRoZSBNaW5pc3RyeSBvZiBEaWdpdGFsIERldmVsb3BtZW50IGFu
ZCBDb21tdW5pY2F0aW9uczEgMB4GA1UEAwwXUnVzc2lhbiBUcnVzdGVkIFJvb3Qg
Q0EwHhcNMjIwMzAxMjEwNDE1WhcNMzIwMjI3MjEwNDE1WjBwMQswCQYDVQQGEwJS
VTE/MD0GA1UECgw2VGhlIE1pbmlzdHJ5IG9mIERpZ2l0YWwgRGV2ZWxvcG1lbnQg
YW5kIENvbW11bmljYXRpb25zMSAwHgYDVQQDDBdSdXNzaWFuIFRydXN0ZWQgUm9v
dCBDQTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAMfFOZ8pUAL3+r2n
qqE0Zp52selXsKGFYoG0GM5bwz1bSFtCt+AZQMhkWQheI3poZAToYJu69pHLKS6Q
XBiwBC1cvzYmUYKMYZC7jE5YhEU2bSL0mX7NaMxMDmH2/NwuOVRj8OImVa5s1F4U
zn4Kv3PFlDBjjSjXKVY9kmjUBsXQrIHeaqmUIsPIlNWUnimXS0I0abExqkbdrXbX
YwCOXhOO2pDUx3ckmJlCMUGacUTnylyQW2VsJIyIGA8V0xzdaeUXg0VZ6ZmNUr5Y
Ber/EAOLPb8NYpsAhJe2mXjMB/J9HNsoFMBFJ0lLOT/+dQvjbdRZoOT8eqJpWnVD
U+QL/qEZnz57N88OWM3rabJkRNdU/Z7x5SFIM9FrqtN8xewsiBWBI0K6XFuOBOTD
4V08o4TzJ8+Ccq5XlCUW2L48pZNCYuBDfBh7FxkB7qDgGDiaftEkZZfApRg2E+M9
G8wkNKTPLDc4wH0FDTijhgxR3Y4PiS1HL2Zhw7bD3CbslmEGgfnnZojNkJtcLeBH
BLa52/dSwNU4WWLubaYSiAmA9IUMX1/RpfpxOxd4Ykmhz97oFbUaDJFipIggx5sX
ePAlkTdWnv+RWBxlJwMQ25oEHmRguNYf4Zr/Rxr9cS93Y+mdXIZaBEE0KS2iLRqa
OiWBki9IMQU4phqPOBAaG7A+eP8PAgMBAAGjZjBkMB0GA1UdDgQWBBTh0YHlzlpf
BKrS6badZrHF+qwshzAfBgNVHSMEGDAWgBTh0YHlzlpfBKrS6badZrHF+qwshzAS
BgNVHRMBAf8ECDAGAQH/AgEEMA4GA1UdDwEB/wQEAwIBhjANBgkqhkiG9w0BAQsF
AAOCAgEAALIY1wkilt/urfEVM5vKzr6utOeDWCUczmWX/RX4ljpRdgF+5fAIS4vH
tmXkqpSCOVeWUrJV9QvZn6L227ZwuE15cWi8DCDal3Ue90WgAJJZMfTshN4OI8cq
W9E4EG9wglbEtMnObHlms8F3CHmrw3k6KmUkWGoa+/ENmcVl68u/cMRl1JbW2bM+
/3A+SAg2c6iPDlehczKx2oa95QW0SkPPWGuNA/CE8CpyANIhu9XFrj3RQ3EqeRcS
AQQod1RNuHpfETLU/A2gMmvn/w/sx7TB3W5BPs6rprOA37tutPq9u6FTZOcG1Oqj
C/B7yTqgI7rbyvox7DEXoX7rIiEqyNNUguTk/u3SZ4VXE2kmxdmSh3TQvybfbnXV
4JbCZVaqiZraqc7oZMnRoWrXRG3ztbnbes/9qhRGI7PqXqeKJBztxRTEVj8ONs1d
WN5szTwaPIvhkhO3CO5ErU2rVdUr89wKpNXbBODFKRtgxUT70YpmJ46VVaqdAhOZ
D9EUUn4YaeLaS8AjSF/h7UkjOibNc4qVDiPP+rkehFWM66PVnP1Msh93tc+taIfC
EYVMxjh8zNbFuoc7fzvvrFILLe7ifvEIUqSVIC/AzplM/Jxw7buXFeGP1qVCBEHq
391d/9RAfaZ12zkwFsl+IKwE/OZxW8AHa9i1p4GO0YSNuczzEm4=
-----END CERTIFICATE-----"""

    private const val RUSSIAN_TRUSTED_SUB_CA = """-----BEGIN CERTIFICATE-----
MIIHQjCCBSqgAwIBAgICEAIwDQYJKoZIhvcNAQELBQAwcDELMAkGA1UEBhMCUlUx
PzA9BgNVBAoMNlRoZSBNaW5pc3RyeSBvZiBEaWdpdGFsIERldmVsb3BtZW50IGFu
ZCBDb21tdW5pY2F0aW9uczEgMB4GA1UEAwwXUnVzc2lhbiBUcnVzdGVkIFJvb3Qg
Q0EwHhcNMjIwMzAyMTEyNTE5WhcNMjcwMzA2MTEyNTE5WjBvMQswCQYDVQQGEwJS
VTE/MD0GA1UECgw2VGhlIE1pbmlzdHJ5IG9mIERpZ2l0YWwgRGV2ZWxvcG1lbnQg
YW5kIENvbW11bmljYXRpb25zMR8wHQYDVQQDDBZSdXNzaWFuIFRydXN0ZWQgU3Vi
IENBMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9YPqBKOk19NFymrE
wehzrhBEgT2atLezpduB24mQ7CiOa/HVpFCDRZzdxqlh8drku408/tTmWzlNH/br
HuQhZ/miWKOf35lpKzjyBd6TPM23uAfJvEOQ2/dnKGGJbsUo1/udKSvxQwVHpVv3
S80OlluKfhWPDEXQpgyFqIzPoxIQTLZ0deirZwMVHarZ5u8HqHetRuAtmO2ZDGQn
vVOJYAjls+Hiueq7Lj7Oce7CQsTwVZeP+XQx28PAaEZ3y6sQEt6rL06ddpSdoTMp
BnCqTbxW+eWMyjkIn6t9GBtUV45yB1EkHNnj2Ex4GwCiN9T84QQjKSr+8f0psGrZ
vPbCbQAwNFJjisLixnjlGPLKa5vOmNwIh/LAyUW5DjpkCx004LPDuqPpFsKXNKpa
L2Dm6uc0x4Jo5m+gUTVORB6hOSzWnWDj2GWfomLzzyjG81DRGFBpco/O93zecsIN
3SL2Ysjpq1zdoS01CMYxie//9zWvYwzI25/OZigtnpCIrcd2j1Y6dMUFQAzAtHE+
qsXflSL8HIS+IJEFIQobLlYhHkoE3avgNx5jlu+OLYe0dF0Ykx1PGNjbwqvTX37R
Cn32NMjlotW2QcGEZhDKj+3urZizp5xdTPZitA+aEjZM/Ni71VOdiOP0igbw6asZ
2fxdozZ1TnSSYNYvNATwthNmZysCAwEAAaOCAeUwggHhMBIGA1UdEwEB/wQIMAYB
Af8CAQAwDgYDVR0PAQH/BAQDAgGGMB0GA1UdDgQWBBTR4XENCy2BTm6KSo9MI7NM
X3vt8jAfBgNVHSMEGDAWgBTh0YHlzlpfBKrS6badZrHF+qwshzCBxwYIKwYBBQUH
AQEEgbswgbgwOgYIKwYBBQUHMAKGLmh0dHA6Ly9yZXBvc2l0b3J5LnJ1c3NpYW50
cnVzdGVkLnJ1L2NhY2VydC5jcnQwNwYIKwYBBQUHMAGGK2h0dHA6Ly9vY3NwLnJ1
c3NpYW50cnVzdGVkLnJ1L2NhY2VydC5jcnQvMDE1BggrBgEFBQcwAoYpaHR0cDov
L3JlcG8uc3Bicy5ydS9jYWNlcnQvdjEtcm9vdC5jcnQwHQYDVR0RBBYwFIISd3d3
Lmdvc3VzbHVnaS5ydTCCAV8GA1UdHwSCAVcwggFTMDigNqA0hjJodHRwOi8vcmVw
b3NpdG9yeS5ydXNzaWFudHJ1c3RlZC5ydS9jcmwvdjEtcm9vdC5jcmwwNqA0oDKG
MGh0dHA6Ly9yZXBvLnNwYnMucnUvY3JsL3YxLXJvb3QuY3JsMDmgN6A1hjNodHRw
Oi8vY2FjZXJ0cy5nb3N1c2x1Z2kucnUvY3JsL3YxLXJvb3QuY3JsMC+gLaArhilo
dHRwOi8vd3d3LnJ1cy1jYS5ydS9jcmwvdjEtcm9vdC5jcmwwN6A1oDOGMWh0dHA6
Ly9yZXBvc2l0b3J5LWF0b2wucnUvY3JsL3YxLXJvb3QuY3JsMDWgM6Axhi9odHRw
Oi8vY2FjZXJ0cy5zb2xhci5ydS9jcmwvdjEtcm9vdC5jcmwwDQYJKoZIhvcNAQEL
BQADggIBACsVdD7jDny5w8s95aL2ZgCjBsmR+s9BqFms4+Jk81N4XpYI4729f6V8
4F+FwIe1V9R74Z75m7Z4v8mY5/Y7+4qY4s+5b0+a5Z26zZg+bV1w7G9z25F280Z
81/B6Z5N2o6j+5s9z8rFz+8V1g4R6jF8O8Y/Kj+s5f9y6F20sK5F75e7W76a5bF
3/Jz+2W9Vv1v057fB01sXF4XF2F6K2V3F2+0W4Z+r2V75k6V9g==
-----END CERTIFICATE-----"""
    private val bundledPems = listOf(
        RUSSIAN_TRUSTED_ROOT_CA,
        RUSSIAN_TRUSTED_SUB_CA
    )

    fun createTrustManager(): X509TrustManager {
        val cf = CertificateFactory.getInstance("X.509")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        // 1. Add all system default trust manager certificates
        val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        defaultTmf.init(null as KeyStore?)
        var counter = 0
        for (tm in defaultTmf.trustManagers) {
            if (tm is X509TrustManager) {
                for (cert in tm.acceptedIssuers) {
                    keyStore.setCertificateEntry("system_${counter++}", cert)
                }
            }
        }

        // 2. Add bundled Russian / Regional certificates
        for ((idx, pem) in bundledPems.withIndex()) {
            try {
                val cert = cf.generateCertificate(ByteArrayInputStream(pem.trim().toByteArray(Charsets.UTF_8))) as? X509Certificate
                if (cert != null) {
                    keyStore.setCertificateEntry("bundled_ru_$idx", cert)
                }
            } catch (_: Exception) {}
        }

        val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        customTmf.init(keyStore)
        return customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    fun createOkHttpClient(): OkHttpClient {
        val trustManager = createTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val defaultOkHttpClient: OkHttpClient by lazy { createOkHttpClient() }
}

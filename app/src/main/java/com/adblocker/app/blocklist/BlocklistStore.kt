package com.adblocker.app.blocklist

import android.content.Context
import com.adblocker.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object BlocklistStore {

    private val domains = ConcurrentHashMap.newKeySet<String>(100_000)
    @Volatile var count = 0
        private set
    @Volatile var loaded = false
        private set

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext

        val cacheFile = File(context.filesDir, "blocklist.cache")
        if (cacheFile.exists()) {
            cacheFile.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    if (line.isNotBlank()) domains.add(line)
                }
            }
        }

        if (domains.isEmpty()) {
            loadFromResource(context)
            cacheFile.bufferedWriter().use { writer ->
                domains.forEach { writer.write(it); writer.newLine() }
            }
        }

        count = domains.size
        loaded = true
    }

    private fun loadFromResource(context: Context) {
        try {
            context.resources.openRawResource(R.raw.hosts).bufferedReader().use { reader ->
                reader.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line -> parseDomain(line) }
                    .forEach { domain -> domains.add(domain) }
            }
        } catch (e: Exception) {
            addBuiltinDomains()
        }
    }

    fun isBlocked(domain: String): Boolean {
        if (domain.isEmpty()) return false
        if (domains.contains(domain)) return true
        var d = domain
        while (d.contains('.')) {
            d = d.substringAfter('.')
            if (domains.contains(d)) return true
        }
        return false
    }

    fun replace(newDomains: Set<String>) {
        domains.clear()
        domains.addAll(newDomains)
        count = domains.size
    }

    fun addDomains(list: Set<String>) {
        domains.addAll(list)
        count = domains.size
    }

    private fun parseDomain(line: String): String? {
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        return parts.getOrNull(1)?.lowercase()?.trim()
    }

    private fun addBuiltinDomains() {
        val builtin = listOf(
            "doubleclick.net", "googleadservices.com", "googlesyndication.com",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "admob.com", "ads.google.com", "ad.doubleclick.net",
            "adservice.google.com.hk", "googleads.g.doubleclick.net",
            "tpc.googlesyndication.com", "adclick.g.doubleclick.net",
            "securepubads.g.doubleclick.net", "pubads.g.doubleclick.net",
            "csi.gstatic.com", "adwords.google.com",
            "app-measurement.com", "firebaseio.com", "crashlytics.com",
            "facebook.com/ads", "facebook.net", "fbcdn.net",
            "amazon-adsystem.com", "aax-us-east.amazon-adsystem.com",
            "mopub.com", "flurry.com", "inmobi.com", "chartboost.com",
            "vungle.com", "unity3d.com/ads", "ironsrc.com",
            "supersonicads.com", "applovin.com", "fyber.com",
            "tapjoy.com", "adcolony.com", "smaato.com",
            "openx.net", "rubiconproject.com", "pubmatic.com",
            "criteo.com", "criteo.net", "casalemedia.com",
            "moatads.com", "adnxs.com", "contextweb.com",
            "outbrain.com", "taboola.com", "revcontent.com",
            "mgid.com", "advertising.com", "yldbt.com",
            "scorecardresearch.com", "quantserve.com", "addthis.com",
            "bluekai.com", "exelator.com", "demdex.net",
            "adsrvr.org", "adroll.com", "adzerk.net",
            "adform.net", "adnxs-simple.com", "bidswitch.net",
            "creativecdn.com", "everesttech.net", "krxd.net",
            "lijit.com", "media.net", "nexac.com",
            "owneriq.net", "rfihub.com", "rlcdn.com",
            "sharethis.com", "simpli.fi", "sitescout.com",
            "spotxchange.com", "tidaltv.com", "tremorhub.com",
            "tribalfusion.com", "turn.com", "videologygroup.com",
            "w55c.net", "yume.com", "zedo.com",
            "zqtk.net", "yahoo.com/ad", "adtechus.com",
            "mathtag.com", "adsrv.eacdn.com", "serving-sys.com",
            "adap.tv", "brightcove.com", "conviva.com",
            "freewheel.tv", "innovid.com", "tremorvideodsp.com",
            "umeng.com", "bugly.qq.com", "beacon.qq.com",
            "h.trace.qq.com", "btrace.qq.com", "log.umsns.com",
            "alog.umeng.com", "oc.umeng.com", "api.umeng.com",
            "baidu.com/ad", "cpro.baidu.com", "e.baidu.com",
            "pos.baidu.com", "cb.baidu.com", "mobads.baidu.com",
            "alimama.com", "tanx.com", "mmstat.com",
            "cpro.suning.com", "dsp.com", "domob.cn",
            "duomeng.cn", "guanggao.com", "tanv.com",
            "adsame.com", "adkmob.com", "adwo.com",
            "miui.com/ad", "xiaomi.com/ad", "mipush.com",
            "huawei.com/ad", "oppo.com/ad", "vivo.com/ad",
            "bytedance.com/ad", "pangle.io", "pangleglobal.com",
            "snssdk.com", "toutiao.com/ad", "tiktok.com/ad",
            "ksapisrv.com", "kwai.com/ad",
            "api.weibo.com/ad", "sina.com.cn/ad",
            "zhihu.com/ad", "bilibili.com/ad",
            "startapp.com", "startappexchange.com", "mintegral.com",
            "rayjump.com", "admaster.com.cn", "growingio.com",
            "umtrack.com", "miaozhen.com", "adbug.cn",
            "trackingio.com", "talkingdata.net", "tendcloud.com",
            "adjust.com", "adjust.io", "appsflyer.com",
            "branch.io", "kochava.com", "singular.net",
            "apsalar.com", "app.link", "applovin.net",
            "localytics.com", "mixpanel.com", "amplitude.com",
            "segment.com", "segment.io", "braze.com",
            "leanplum.com", "clevertap.com", "moengage.com",
            "onesignal.com", "airship.com", "iterable.com",
            "customer.io", "userlist.com", "user.com"
        )
        domains.addAll(builtin)
    }
}

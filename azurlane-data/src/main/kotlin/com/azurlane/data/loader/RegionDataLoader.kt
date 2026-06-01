package com.azurlane.data.loader

interface RegionDataLoader {
    val regionCode: String
    fun loadData(basePath: String)
}

object RegionDataLoaderFactory {
    private val loaders = mapOf(
        "CN" to CnDataLoader
    )

    fun getLoader(regionCode: String): RegionDataLoader {
        return loaders[regionCode]
            ?: throw IllegalArgumentException("unsupported region: $regionCode")
    }

    fun getAvailableRegions(): Set<String> = loaders.keys
}

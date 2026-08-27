package com.happycola233.bilitools.core.naming

/**
 * 一次命名渲染所需的全部取值。字段一律「取不到就是 null」，
 * 由模板决定要不要用，渲染器不做跨层级的兜底替换。
 */
data class NamingContext(
    /** 叶子标题：分 P 标题、单集名、歌名、图文标题。 */
    val title: String? = null,
    /** 所属作品：稿件标题、番剧季名、课程名、歌名、图文标题。 */
    val work: String? = null,
    /** 上层集合：UGC 合集、收藏夹、歌单、文集、用户列表。 */
    val collection: String? = null,
    /** 官方分 P 号，只有稿件视频有。 */
    val p: String? = null,
    /** 集号：番剧 title、课程分集数。 */
    val ep: String? = null,
    /** 分区：番剧的正片/PV/特典，UGC 合集的分部。 */
    val section: String? = null,
    /** 图文内的图片序号。 */
    val img: String? = null,
    /** 入口类型文案。 */
    val container: String? = null,
    /** 条目类型文案。 */
    val mediaType: String? = null,
    /** 任务类型文案。 */
    val taskType: String? = null,
    /** 本批下载中的序号，从 1 起。 */
    val index: Int? = null,
    val pubTimeEpochSeconds: Long? = null,
    val downTimeEpochSeconds: Long? = null,
    val upper: String? = null,
    val upperId: String? = null,
    /** 音频作者，常与 UP 主不同。 */
    val artist: String? = null,
    /** 公开内容号：BV / ep / au / cv。 */
    val id: String? = null,
    val aid: String? = null,
    val bvid: String? = null,
    val cid: String? = null,
    val epid: String? = null,
    val ssid: String? = null,
    val sid: String? = null,
    val amid: String? = null,
    val fid: String? = null,
    val opid: String? = null,
    val cvid: String? = null,
    val res: String? = null,
    val abr: String? = null,
    val enc: String? = null,
    val fmt: String? = null,
)

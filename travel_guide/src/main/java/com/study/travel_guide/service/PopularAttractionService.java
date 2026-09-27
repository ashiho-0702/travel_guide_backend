package com.study.travel_guide.service;

import com.study.travel_guide.dto.PopularAttraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 城市热门景点（预置精选名称，非 AI 生成）。图片动态配图：高德搜照片 → 下载 → 转存 OSS → Redis 缓存 30 天。
 * 加城市只需在 DATA 里加城市 + 景点名，无需上传图片。
 */
@Slf4j
@Service
public class PopularAttractionService {

    private static final Map<String, List<PopularAttraction>> DATA = Map.of(
            "北京", List.of(
                    a("故宫", "明清两代皇宫，世界文化遗产"),
                    a("八达岭长城", "万里长城最著名的一段"),
                    a("颐和园", "皇家园林，昆明湖与万寿山"),
                    a("天坛", "明清帝王祭天之所"),
                    a("天安门广场", "北京地标，升旗仪式"),
                    a("南锣鼓巷", "老北京胡同与文创小店")
            ),
            "上海", List.of(
                    a("外滩", "万国建筑群与黄浦江夜景"),
                    a("东方明珠", "陆家嘴地标，登塔俯瞰全城"),
                    a("豫园", "江南古典园林与城隍庙"),
                    a("上海迪士尼", "主题乐园，亲子首选"),
                    a("田子坊", "石库门里的文艺街区"),
                    a("南京路步行街", "百年商业街")
            ),
            "杭州", List.of(
                    a("西湖", "湖光山色，免费开放的经典"),
                    a("灵隐寺", "千年古刹，飞来峰石刻"),
                    a("西溪湿地", "城市湿地，摇橹船体验"),
                    a("雷峰塔", "西湖十景，白蛇传传说"),
                    a("河坊街", "南宋御街，美食手信"),
                    a("宋城", "宋代风情主题景区")
            ),
            "成都", List.of(
                    a("宽窄巷子", "成都休闲生活代表"),
                    a("成都大熊猫基地", "看熊猫幼崽，萌翻全场"),
                    a("武侯祠", "三国文化，诸葛亮纪念地"),
                    a("锦里", "川西民俗与小吃街"),
                    a("都江堰", "千年水利工程"),
                    a("春熙路", "市中心商圈与太古里")
            ),
            "西安", List.of(
                    a("秦始皇兵马俑", "世界第八大奇迹"),
                    a("大雁塔", "唐代佛塔，大慈恩寺"),
                    a("西安城墙", "现存最完整的古城墙"),
                    a("回民街", "西北美食聚集地"),
                    a("华清宫", "唐代皇家温泉行宫"),
                    a("钟鼓楼", "西安老城中心地标")
            ),
            "三亚", List.of(
                    a("亚龙湾", "沙细水清的海滩"),
                    a("天涯海角", "浪漫海滨地标"),
                    a("南山文化旅游区", "海上观音与佛教文化"),
                    a("蜈支洲岛", "潜水与海岛游玩"),
                    a("大东海", "市区近便的海滨浴场"),
                    a("鹿回头", "登高俯瞰三亚湾")
            )
    );

    private final AmapService amapService;
    private final OssService ossService;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;

    public PopularAttractionService(AmapService amapService, OssService ossService,
                                    StringRedisTemplate redisTemplate, RestClient restClient) {
        this.amapService = amapService;
        this.ossService = ossService;
        this.redisTemplate = redisTemplate;
        this.restClient = restClient;
    }

    public List<PopularAttraction> popular(String city) {
        List<PopularAttraction> list = DATA.getOrDefault(city, List.of());
        for (PopularAttraction p : list) {
            p.setImageUrl(resolvePhoto(city, p.getName()));
        }
        return list;
    }

    /**
     * 预热：遍历所有城市/景点，逐个 resolvePhoto（Redis 命中则跳过）。返回成功配到图的景点数。
     */
    public int warmUp() {
        int count = 0;
        for (Map.Entry<String, List<PopularAttraction>> e : DATA.entrySet()) {
            for (PopularAttraction p : e.getValue()) {
                if (resolvePhoto(e.getKey(), p.getName()) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private String resolvePhoto(String city, String name) {
        String cacheKey = "attraction:photo:" + city + ":" + name;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String amapUrl = amapService.searchPhotoUrl(city, name);
        if (amapUrl == null) {
            return null;
        }
        try {
            byte[] bytes = restClient.get().uri(amapUrl).retrieve().body(byte[].class);
            String objectName = "attractions/poi/" + ((city + "-" + name).hashCode() & Integer.MAX_VALUE) + ".jpg";
            String ossUrl = ossService.upload(bytes, objectName);
            redisTemplate.opsForValue().set(cacheKey, ossUrl, Duration.ofDays(30));
            return ossUrl;
        } catch (Exception e) {
            log.warn("[attraction] 照片转存失败 {}: {}", name, e.getMessage());
            return null;
        }
    }

    private static PopularAttraction a(String name, String desc) {
        PopularAttraction p = new PopularAttraction();
        p.setName(name);
        p.setDescription(desc);
        return p;
    }
}

package com.study.travel_guide.service;

import com.study.travel_guide.dto.PopularAttraction;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 城市热门景点（预置数据，非 AI 生成）。生成前展示给用户勾选。
 * 图片 URL 硬编码为 OSS 路径，团队需按「attractions/{城市拼音}/{景点拼音}.jpg」上传真实景点图。
 */
@Service
public class PopularAttractionService {

    private static final String IMG = "https://javaweb-study-gdou.oss-cn-beijing.aliyuncs.com/attractions/";

    private static final Map<String, List<PopularAttraction>> DATA = Map.of(
            "北京", List.of(
                    a("故宫", "明清两代皇宫，世界文化遗产", "beijing/gugong"),
                    a("八达岭长城", "万里长城最著名的一段", "beijing/changcheng"),
                    a("颐和园", "皇家园林，昆明湖与万寿山", "beijing/yiheyuan"),
                    a("天坛", "明清帝王祭天之所", "beijing/tiantan"),
                    a("天安门广场", "北京地标，升旗仪式", "beijing/tiananmen"),
                    a("南锣鼓巷", "老北京胡同与文创小店", "beijing/nanluoguxiang")
            ),
            "上海", List.of(
                    a("外滩", "万国建筑群与黄浦江夜景", "shanghai/waitan"),
                    a("东方明珠", "陆家嘴地标，登塔俯瞰全城", "shanghai/dongfangmingzhu"),
                    a("豫园", "江南古典园林与城隍庙", "shanghai/yuyuan"),
                    a("上海迪士尼", "主题乐园，亲子首选", "shanghai/disney"),
                    a("田子坊", "石库门里的文艺街区", "shanghai/tianzifang"),
                    a("南京路步行街", "百年商业街", "shanghai/nanjinglu")
            ),
            "杭州", List.of(
                    a("西湖", "湖光山色，免费开放的经典", "hangzhou/xihu"),
                    a("灵隐寺", "千年古刹，飞来峰石刻", "hangzhou/lingyinsi"),
                    a("西溪湿地", "城市湿地，摇橹船体验", "hangzhou/xixishidi"),
                    a("雷峰塔", "西湖十景，白蛇传传说", "hangzhou/leifengta"),
                    a("河坊街", "南宋御街，美食手信", "hangzhou/hefangjie"),
                    a("宋城", "宋代风情主题景区", "hangzhou/songcheng")
            ),
            "成都", List.of(
                    a("宽窄巷子", "成都休闲生活代表", "chengdu/kuanzhaixiangzi"),
                    a("成都大熊猫基地", "看熊猫幼崽，萌翻全场", "chengdu/xiongmaojidi"),
                    a("武侯祠", "三国文化，诸葛亮纪念地", "chengdu/wuhouci"),
                    a("锦里", "川西民俗与小吃街", "chengdu/jinli"),
                    a("都江堰", "千年水利工程", "chengdu/dujiangyan"),
                    a("春熙路", "市中心商圈与太古里", "chengdu/chunxilu")
            ),
            "西安", List.of(
                    a("秦始皇兵马俑", "世界第八大奇迹", "xian/bingmayong"),
                    a("大雁塔", "唐代佛塔，大慈恩寺", "xian/dayanta"),
                    a("西安城墙", "现存最完整的古城墙", "xian/chengqiang"),
                    a("回民街", "西北美食聚集地", "xian/huiminjie"),
                    a("华清宫", "唐代皇家温泉行宫", "xian/huaqinggong"),
                    a("钟鼓楼", "西安老城中心地标", "xian/zhonggulou")
            ),
            "三亚", List.of(
                    a("亚龙湾", "沙细水清的海滩", "sanya/yalongwan"),
                    a("天涯海角", "浪漫海滨地标", "sanya/tianyahaijiao"),
                    a("南山文化旅游区", "海上观音与佛教文化", "sanya/nanshan"),
                    a("蜈支洲岛", "潜水与海岛游玩", "sanya/wuzhizhoudao"),
                    a("大东海", "市区近便的海滨浴场", "sanya/dadonghai"),
                    a("鹿回头", "登高俯瞰三亚湾", "sanya/luhuitou")
            )
    );

    public List<PopularAttraction> popular(String city) {
        return DATA.getOrDefault(city, List.of());
    }

    private static PopularAttraction a(String name, String desc, String slug) {
        PopularAttraction p = new PopularAttraction();
        p.setName(name);
        p.setDescription(desc);
        p.setImageUrl(IMG + slug + ".jpg");
        return p;
    }
}

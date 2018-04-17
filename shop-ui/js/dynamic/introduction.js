/**
 * Created by za-wangshenhua on 2018/3/29.
 */
$(function(){

    var id = getQueryVariable("commodity_id");
    if(undefined != id && null != id && "" != id){
        /**
         * 查询商品详情
         */
        $.ajax({
            type: 'GET',
            contentType: "application/json;",
            url: domain + "api/search/findCommodityFromESById/" + id,
            success: function(result){
                if(result.status == 0){
                    var items = result.items;
                    $(".tb-detail-hd").attr("commodity_id", items.commodity_id);
                    $(".tb-detail-hd h1").html(items.commodity_name);
                    //设置浏览图片
                    $.each(items.image_url, function(index, image_urls) {
                        if (0 == index) {
                            $(".tb-thumb").append("<li class=\"tb-selected\"><div class=\"tb-pic tb-s40\"><a href=\"javascript:void(0)\"><img src=\"" + image_urls + "\" mid=\"" + image_urls + "\" big=\"" + image_urls + "\"></a></div></li>");
                            $("#thumblist li a").click();
                        } else {
                            $(".tb-thumb").append("<li><div class=\"tb-pic tb-s40\"><a href=\"javascript:void(0)\"><img src=\"" + image_urls + "\" mid=\"" + image_urls + "\" big=\"" + image_urls + "\"></a></div></li>");
                        }
                    });
                    $(".tm-ind-sellCount .tm-count").html(items.month_sales_volume);
                    $(".tm-ind-sumCount .tm-count").html(items.total_evaluation_num);
                    $(".tm-ind-reviewCount .tm-count").html(items.total_sales_volume);
                    $(".sys_item_price").html(items.price);
                    $(".sys_item_mktprice").html(items.original_price);
                    $(".sys_item_freprice").html(items.freight);
                    $(".stock").html(items.num);
                    //设置产品参数
                    var params = items.commodity_params;
                    console.log(items)
                    $.each(items.detail_image_url, function(index, detail_image_url) {
                        $(".twlistNews").append("<img src=\"" + detail_image_url + "\">");
                    });
                }
            },
            error: function () {
                console.log("网络异常");
            }
        });
    } else {
        alert("获取商品信息失败！");
    }

    $("#LikBuy").click(function () {
        window.location.href = "/pay.html?commodity_id=" + id + "&order_num=" + $("#text_box").val();
    });

    /**
     * 搜索
     */
    $(".submit").click(function(){
        var keyword = encodeURI($("input[name='index_none_header_sysc']").val());
        window.location.href = "/search.html?keyword=" + keyword;
    });
    /**
     * enter键自动提交
     */
    $("#searchInput").bind('keypress',function(event){
        if(event.keyCode == "13")
        {
            $("#ai-topsearch").click();
        }
    });

    $("#LikBasket").click(function () {
        var data ={
            commodity_id: parseInt($(".tb-detail-hd").attr("commodity_id")),
            commodity_name: $(".tb-detail-hd h1").html(),
            price: $(".sys_item_price").html(),
            original_price: $(".sys_item_mktprice").html(),
            order_num: parseInt($("#text_box").val()),
            image_url: $("#thumblist").find("li:first-child").find("img").attr("src")
        }
        $.ajax({
            type: 'POST',
            contentType: "application/json;",
            url: domain + "api/cart/insertCart",
            data: JSON.stringify(data),
            success: function(result){
                if(result.status == 0){
                    console.log(result);

                }
            },
            error: function () {
                console.log("网络异常");
            }
        });
    })
});
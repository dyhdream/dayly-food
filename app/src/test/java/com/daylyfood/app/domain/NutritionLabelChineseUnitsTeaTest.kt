package com.daylyfood.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real back label: 东方树叶茉莉花茶. Two things it punishes:
 *  - units printed as 克 / 毫克 instead of g / mg
 *  - every value is legitimately 0, which must not be read as "no label found"
 *  - and the 配料表 below carries numbers (4g/L, 21733, SC...) that must not leak in
 */
class NutritionLabelChineseUnitsTeaTest {

    private val ocrText = """
        东方树叶茉莉花茶
        茶多酚含量≥280mg/kg
        营养成分表
        项目 每100毫升 营养素参考值%
        能量 0千焦 0%
        蛋白质 0克 0%
        脂肪 0克 0%
        —饱和脂肪 0克 0%
        碳水化合物 0克 0%
        —糖 0克 0%
        钠 0毫克 0%
        儿童青少年应避免过量摄入盐油糖。
        配料表:水、茉莉花茶(绿茶茶坯)(≥4g/L)、维生素C、碳酸氢钠
        产品名称:东方树叶茉莉花茶原味茶饮料 产品类别:其他调味茶饮料
        产品标准号:GB/T 21733 委托方:农夫山泉股份有限公司
        地址:浙江省杭州市西湖区葛衙庄181号
        生产许可证编号:SC10644160200149
        www.nongfuspring.com 全国服务热线:95077
    """.trimIndent()

    @Test
    fun `all-zero tea label is parsed as a real label, not as empty`() {
        val label = NutritionLabelParser.parse(ocrText)

        assertFalse("全零标签不能被当成没识别到", label.isEmpty)
        assertEquals(0.0, label.energyKj!!, 0.0001)
        assertEquals(0.0, label.kcal!!, 0.0001)
        assertEquals(0.0, label.protein!!, 0.0001)
        assertEquals(0.0, label.fat!!, 0.0001)
        assertEquals(0.0, label.carb!!, 0.0001)
        assertEquals(0.0, label.sodiumMg!!, 0.0001)
        assertFalse(label.isSuspicious())
    }

    @Test
    fun `ingredient list numbers never leak into nutrition values`() {
        val label = NutritionLabelParser.parse(ocrText)

        // 4g/L from the tea leaf, 280mg/kg from 茶多酚, SC... codes: none may win a field.
        assertEquals(0.0, label.protein!!, 0.0001)
        assertEquals(0.0, label.sodiumMg!!, 0.0001)
    }

    @Test
    fun `sugar grams in the ingredient list after the table are ignored`() {
        val text = """
            营养成分表
            项目 每100克
            能量 1650千焦
            蛋白质 6.5克
            脂肪 3.2克
            碳水化合物 78.0克
            钠 120毫克
            配料表:小麦粉、白砂糖(22克)、植物油、食用盐
            产品名称:某饼干
        """.trimIndent()

        val label = NutritionLabelParser.parse(text)

        assertEquals(6.5, label.protein!!, 0.0001)
        assertEquals(3.2, label.fat!!, 0.0001)
        assertEquals(78.0, label.carb!!, 0.0001)
        assertEquals(120.0, label.sodiumMg!!, 0.0001)
    }

    @Test
    fun `label without the nutrition header still parses`() {
        val label = NutritionLabelParser.parse("每100克 蛋白质 6克 脂肪 2克 碳水化合物 70克 钠 90毫克")
        assertEquals(6.0, label.protein!!, 0.0001)
        assertEquals(70.0, label.carb!!, 0.0001)
    }

    @Test
    fun `text with no table data stays empty`() {
        val label = NutritionLabelParser.parse("东方树叶茉莉花茶 茶多酚含量≥280mg/kg")
        assertNull(label.kcal)
    }
}

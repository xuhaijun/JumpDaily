package com.jumpdaily.jump.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jumpdaily.jump.ui.components.CuteButton
import com.jumpdaily.jump.ui.navigation.Screen

/**
 * 隐私政策页：以「最小必要」原则说明本应用收集哪些信息、如何存储与保护、
 * 用到了哪些权限与第三方 SDK，并满足儿童类应用的告知与监护人同意义务。
 * 该正文同时是首启「同意框」所指向的详情。
 */
@Composable
fun PrivacyScreen(nav: NavHostController) {
    // 注意：下列正文为可上架的正式版本；联系邮箱已配置为运营者邮箱（见「九、联系我们」）。
    val sections = listOf(
        "一、导言与适用范围" to "欢迎使用「爱跳绳」（以下简称本应用）。本应用是一款帮助儿童养成跳绳运动习惯的本地运动记录工具，由本应用运营者（以下简称我们）负责运营。\n" +
                "本政策依据《中华人民共和国个人信息保护法》《儿童个人信息网络保护规定》等相关法律法规制定，旨在清晰说明我们如何收集、使用、存储与保护用户信息。\n" +
                "本应用主要面向未成年人（儿童）使用，请儿童用户的父母或其他监护人（以下简称监护人）在儿童使用前仔细阅读本政策，并在充分理解后代表儿童作出同意。若监护人不同意本政策，请勿使用本应用。",
        "二、我们收集的信息及用途" to "我们严格遵循「合法、正当、必要、最小限度」原则收集信息，所收集信息仅用于实现跳绳记录与提醒功能，且全部在您设备本地处理。具体为：\n" +
                "1. 儿童昵称：由您自行填写，仅用于在本机区分不同儿童的锻炼记录，不构成身份识别信息。\n" +
                "2. 跳绳运动数据：包括跳绳次数、运动时长、最长连续次数、消耗热量估算等，全部保存在您本机数据库。\n" +
                "3. 传感器数据：在「传感器模式」下调用加速度传感器进行实时跳绳计数，数据仅在设备本地实时计算，我们不作读取、不收集、不上传。\n" +
                "4. 摄像头画面：在「摄像头模式」下用于本地姿态识别计数，画面仅在设备本地实时处理，不上传、不保存、不用于任何其他目的。\n" +
                "5. 提醒偏好：您设定的每日提醒开关与时间，保存在本机。\n" +
                "我们不会收集与前述功能无关的个人信息，亦不收集身份证号、精准定位、通讯录、短信、相册等敏感个人信息。",
        "三、权限调用说明" to "为实现上述功能，本应用会在您主动触发相关功能时申请下列系统权限，且均不强制、可随时在系统设置中关闭：\n" +
                "• 摄像头权限：仅在您使用「摄像头模式」计数时调用，用于本地姿态识别，画面不离开本机。\n" +
                "• 通知权限：仅在您开启「每日提醒」时调用，用于向您发送跳绳提醒；您可随时在系统设置中关闭通知。\n" +
                "• 振动权限：用于计数与完成的触感反馈。\n" +
                "若您拒绝授予某项权限，仅会导致对应功能不可用，不影响其他功能的正常使用。",
        "四、信息的存储与安全措施" to "1. 存储位置：上述全部个人信息均存储于您所使用的这台设备本地（采用 Room 本地数据库与 DataStore 存储），不会上传至任何服务器。\n" +
                "2. 安全措施：我们采用符合业界标准的安全防护措施保护本地数据，防止数据遭到未经授权的访问、泄露、篡改或丢失。\n" +
                "3. 数据清除：您可随时在应用内删除某位儿童的记录或全部数据；卸载本应用后，与之相关的全部本地数据将一并被清除，无法恢复。",
        "五、信息的共享、转让与公开披露" to "我们不会向任何第三方共享、转让或公开披露您的个人信息，除非符合下列情形之一：\n" +
                "• 事先获得您的明确同意；\n" +
                "• 根据法律法规、监管要求或司法/行政机关的强制性规定必须提供；\n" +
                "• 为保护您或公众的合法权益、财产安全及人身安全所必需的。\n" +
                "发生上述情形需对外提供个人信息的，我们将依法进行脱敏处理，并严格按照法律规定执行。",
        "六、儿童个人信息保护" to "本应用专为儿童运动场景设计，我们高度重视儿童个人信息保护：\n" +
                "• 坚持「最小必要」原则，仅收集实现跳绳记录所必需的信息，绝不收集与运动无关的信息。\n" +
                "• 绝不收集身份证号、精准地理位置、通讯录、人脸等敏感生物识别信息（摄像头画面仅本地实时处理，不作留存）。\n" +
                "• 在使用前取得监护人同意；如监护人撤回同意，我们将停止相关功能并删除已收集的本地数据。\n" +
                "• 如您认为儿童个人信息受到侵害，可通过本政策「九、联系我们」中的方式与我们联系。",
        "七、您的权利" to "您及监护人对本人（及所监护儿童）的个人信息享有下列权利，可通过应用内功能或联系我们行使：\n" +
                "• 查阅、复制：在应用内查看已保存的运动记录与设置。\n" +
                "• 更正、补充：在应用内修改儿童昵称、提醒设置等信息。\n" +
                "• 删除：在应用内删除指定儿童或全部记录；卸载应用即删除全部本地数据。\n" +
                "• 撤回同意：随时关闭相关权限或在系统设置中停止授权，撤回不影响撤回前已进行的处理。\n" +
                "行使上述权利无需支付费用，我们将在法律法规规定的期限内予以响应。",
        "八、第三方 SDK 清单" to "本应用仅集成用于实现本地功能的官方及必要第三方组件，均不涉及数据对外传输：\n" +
                "• MediaPipe Tasks Vision（Google）：本地姿态识别，数据仅在设备内处理，不上传。\n" +
                "• CameraX、WorkManager、DataStore、Room（Android 官方组件）：均为设备端本地能力，数据不出本机。\n" +
                "本应用不集成任何广告 SDK，也不集成任何数据统计、行为分析或用户画像类上报 SDK。",
        "九、联系我们" to "如您对本政策或儿童个人信息处理有任何疑问、意见或投诉，可通过以下方式与我们联系：\n" +
                "• 联系邮箱：xuhaijun5382@163.com\n" +
                "我们将在收到诉求后依法及时核实并答复。",
        "十、政策更新" to "我们可能因功能调整或法律法规变化更新本政策。更新后的政策将在应用内予以展示，并在涉及重大变更时重新征求您的同意。请您定期关注本政策的最新版本。本政策最近更新日期与生效日期见页首标注。"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("隐私政策", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("最近更新：2026-09-02 · 生效日期：2026-09-02", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        sections.forEach { (title, body) ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CuteButton("我已阅读并同意", onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth())
    }
}

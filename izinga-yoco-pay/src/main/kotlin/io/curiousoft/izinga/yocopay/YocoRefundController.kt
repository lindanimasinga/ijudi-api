package io.curiousoft.izinga.yocopay

import com.fasterxml.jackson.databind.ObjectMapper
import io.curiousoft.izinga.yocopay.api.YocoPaymentClient
import io.curiousoft.izinga.yocopay.api.YocoRefundRequest
import io.curiousoft.izinga.yocopay.config.YocoConfiguration
import io.curiousoft.izinga.yocopay.config.yocoHash
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest


@RestController
@RequestMapping("/yoco/refund")
class YocoRefundController(private val yocoConfiguration: YocoConfiguration,
                           private val izingaOrderMananger: IzingaOrderManagerClient,
                           private val yocoPaymentClient: YocoPaymentClient,
                           private val mapper: ObjectMapper
) {

    val log = LoggerFactory.getLogger(this::class.java)

}

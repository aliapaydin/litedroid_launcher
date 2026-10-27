package dev.litedroid.launcher.draw

/** Çizilecek kelime: Türkçesi, İngilizcesi (yapay zekâya resim tarifi için) ve zorluğu (1-3). */
data class DrawWord(val tr: String, val en: String, val level: Int)

/**
 * Çiz-Bil kelime havuzu: çizmesi eğlenceli, evdeki herkesin bildiği şeyler.
 * Çizen her turda üç kelimeden birini seçer (birer kolay, orta ve zor).
 */
object DrawWords {
    private val easy = """
        kedi|cat, köpek|dog, balık|fish, kuş|bird, elma|apple, muz|banana, armut|pear, çilek|strawberry,
        güneş|sun, ay|moon, yıldız|star, bulut|cloud, ağaç|tree, çiçek|flower, ev|house, araba|car,
        top|ball, kalp|heart, göz|eye, el|hand, ayak|foot, şapka|hat, gözlük|glasses, ayakkabı|shoe,
        bardak|glass of water, kaşık|spoon, çatal|fork, bıçak|knife, tabak|plate, sandalye|chair, masa|table,
        kapı|door, pencere|window, anahtar|key, saat|clock, telefon|phone, kitap|book, kalem|pencil,
        yumurta|egg, dondurma|ice cream, pasta|cake, pizza|pizza, karpuz|watermelon, havuç|carrot,
        yılan|snake, tavşan|rabbit, ördek|duck, kurbağa|frog, örümcek|spider, kelebek|butterfly, arı|bee,
        salyangoz|snail, gemi|ship, uçak|airplane, bisiklet|bicycle, tren|train, balon|balloon, şemsiye|umbrella,
        kardan adam|snowman, gökkuşağı|rainbow, dağ|mountain, deniz|sea waves, ateş|fire, mum|candle,
        hediye|gift box, mektup|envelope, gitar|guitar, davul|drum, televizyon|television, lamba|lamp
    """
    private val medium = """
        zürafa|giraffe, fil|elephant, aslan|lion, maymun|monkey, penguen|penguin, köpekbalığı|shark,
        ahtapot|octopus, timsah|crocodile, kaplumbağa|turtle, deve|camel, baykuş|owl, at|horse, inek|cow,
        tavuk|chicken, horoz|rooster, kirpi|hedgehog, sincap|squirrel, kanguru|kangaroo, yunus|dolphin,
        balina|whale, dinozor|dinosaur, ejderha|dragon, robot|robot, uzaylı|alien, roket|rocket,
        helikopter|helicopter, denizaltı|submarine, otobüs|bus, kamyon|truck, traktör|tractor, ambulans|ambulance,
        deniz feneri|lighthouse, köprü|bridge, kale|castle, çadır|tent, piramit|pyramid, yel değirmeni|windmill,
        palmiye|palm tree, kaktüs|cactus, mantar|mushroom, yanardağ|volcano, ada|island, şelale|waterfall,
        kum saati|hourglass, makas|scissors, çekiç|hammer, merdiven|ladder, yastık|pillow, yatak|bed,
        buzdolabı|refrigerator, çaydanlık|teapot, tencere|cooking pot, hamburger|hamburger, simit|bagel,
        patlamış mısır|popcorn, kurabiye|cookie, ananas|pineapple, üzüm|grapes, kiraz|cherries,
        taç|crown, kılıç|sword, kalkan|shield, hazine sandığı|treasure chest, korsan|pirate, prenses|princess,
        sihirbaz|wizard, hayalet|ghost, cadı|witch, palyaço|clown, futbol|soccer ball, kale direği|soccer goal,
        basketbol|basketball hoop, kayak|skis, sörf|surfboard, kaykay|skateboard, yüzme havuzu|swimming pool,
        kamera|camera, kulaklık|headphones, mikrofon|microphone, piyano|piano, keman|violin, pusula|compass
    """
    private val hard = """
        astronot|astronaut, dalgıç|scuba diver, itfaiyeci|firefighter, aşçı|chef, doktor|doctor, polis|police officer,
        dedektif|detective, ressam|painter at an easel, berber|barber, çiftçi|farmer, kral|king,
        uzay gemisi|spaceship, uydu|satellite, gezegen|planet with rings, kuyruklu yıldız|comet, karadelik|black hole,
        dönme dolap|ferris wheel, hız treni|roller coaster, atlıkarınca|carousel, sirk|circus tent,
        uyurgezer|sleepwalker, horlamak|snoring person, hapşırmak|sneezing person,
        mıknatıs|magnet, ampul|light bulb, pil|battery, priz|power socket, mikroskop|microscope, teleskop|telescope,
        bumerang|boomerang, sapan|slingshot, uçurtma|kite, salıncak|swing, kaydırak|slide at a playground,
        kumdan kale|sandcastle, yılbaşı ağacı|christmas tree, havai fişek|fireworks, doğum günü|birthday party,
        düğün|wedding cake, mezuniyet|graduation cap, buz pateni|ice skating, paraşüt|parachute, sıcak hava balonu|hot air balloon,
        trafik lambası|traffic light, benzin istasyonu|gas station, asansör|elevator, yürüyen merdiven|escalator,
        çamaşır makinesi|washing machine, elektrikli süpürge|vacuum cleaner, saç kurutma makinesi|hair dryer, diş fırçası|toothbrush,
        örümcek ağı|spider web, arı kovanı|beehive, kuş yuvası|bird nest, karınca yuvası|ant hill, deniz kızı|mermaid,
        tek boynuzlu at|unicorn, mumya|mummy, vampir|vampire, ninja|ninja, şövalye|knight in armor, kovboy|cowboy,
        selfie|taking a selfie, bilgisayar faresi|computer mouse, kumanda|tv remote control, oyun konsolu|game controller
    """

    private fun parse(block: String, level: Int): List<DrawWord> =
        block.split(',').map { it.trim() }.filter { it.contains('|') }.map {
            DrawWord(it.substringBefore('|').trim(), it.substringAfter('|').trim(), level)
        }

    val all: List<DrawWord> by lazy { parse(easy, 1) + parse(medium, 2) + parse(hard, 3) }

    private val recent = ArrayList<String>()

    /** Çizene sunulacak üç kelime: bir kolay, bir orta, bir zor; yakında çıkmış olanlardan kaçınır. */
    @Synchronized
    fun choices(): List<DrawWord> = (1..3).map { lvl -> pick(lvl) }

    /** Yapay zekânın çizeceği kelime: orta zorlukta, somut bir şey. */
    @Synchronized
    fun forAi(): DrawWord = pick(if (Math.random() < 0.7) 2 else 1)

    private fun pick(level: Int): DrawWord {
        val pool = all.filter { it.level == level }
        val w = pool.filter { it.tr !in recent }.randomOrNull() ?: pool.random()
        recent.add(w.tr)
        while (recent.size > 90) recent.removeAt(0)
        return w
    }
}

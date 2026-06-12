package com.cayadev.orbo.engine

import android.content.Context

/**
 * Lightweight in-code localization. The whole app draws strings on a Canvas, so instead of the
 * Android resource framework we translate English source strings to the active language at draw
 * time via [t]. Language defaults to the system locale on first launch and can be overridden from
 * Settings (persisted in [Scores]).
 */
object Loc {
    enum class Lang(val code: String, val label: String) { EN("en", "EN"), TR("tr", "TR") }

    var lang: Lang = Lang.EN
        private set

    private var scores: Scores? = null

    fun init(context: Context, scores: Scores) {
        this.scores = scores
        lang = when (scores.getString("lang", "")) {
            "tr" -> Lang.TR
            "en" -> Lang.EN
            else -> if (systemIsTurkish(context)) Lang.TR else Lang.EN   // auto: follow the watch
        }
    }

    private fun systemIsTurkish(context: Context): Boolean =
        runCatching { context.resources.configuration.locales[0].language == "tr" }.getOrDefault(false)

    fun set(l: Lang) {
        lang = l
        scores?.setString("lang", l.code)
    }

    fun toggle() = set(if (lang == Lang.EN) Lang.TR else Lang.EN)

    /** Translate an English source string. Unknown strings pass through unchanged. */
    fun t(en: String): String {
        if (lang == Lang.EN) return en
        return tr[en] ?: en
    }

    private val tr: Map<String, String> = mapOf(
        // --- categories ---
        "Featured" to "Öne Çıkan",
        "Arcade" to "Atari",
        "Reflex" to "Refleks",
        "Puzzle" to "Bulmaca",
        "Brain" to "Zekâ",
        "Classic" to "Klasik",
        "Tools" to "Araçlar",

        // --- game chrome ---
        "TAP TO PLAY" to "OYNAMAK İÇİN DOKUN",
        "GAME OVER" to "OYUN BİTTİ",
        "PAUSED" to "DURAKLATILDI",
        "RESUME" to "DEVAM ET",
        "RESTART" to "YENİDEN BAŞLA",
        "RETRY" to "TEKRAR DENE",
        "EXIT" to "ÇIKIŞ",
        "EXIT TO MENU" to "MENÜYE ÇIK",
        "★ NEW BEST ★" to "★ YENİ REKOR ★",
        "Exit game?" to "Oyundan çıkılsın mı?",
        "Progress will be lost" to "İlerleme kaybolacak",
        "YES" to "EVET",
        "NO" to "HAYIR",
        "BEST" to "REKOR",
        "GAMES" to "OYUN",
        "game" to "oyun",
        "games" to "oyun",
        "EPIC" to "EFSANE",
        "BIG" to "BÜYÜK",

        // --- menus ---
        "WRIST ARCADE" to "BİLEK ATARİSİ",
        "POCKET ARCADE" to "CEP ATARİSİ",
        "SELECT LEVEL" to "BÖLÜM SEÇ",
        "LOCKED" to "KİLİTLİ",
        "Settings" to "Ayarlar",
        "SOUND" to "SES",
        "HAPTICS" to "TİTREŞİM",
        "LANGUAGE" to "DİL",

        // --- game over titles ---
        "BOARD JAMMED" to "TAHTA DOLDU",
        "TIME UP" to "SÜRE DOLDU",
        "NO MOVES" to "HAMLE YOK",
        "RESULTS" to "SONUÇLAR",

        // --- control hints ---
        "TAP PIECE THEN BOARD" to "PARÇAYA SONRA TAHTAYA DOKUN",
        "DRAG TO MOVE PADDLE" to "RAKETİ SÜRÜKLE",
        "POP · 45 SECONDS" to "PATLAT · 45 SANİYE",
        "DRAG THE BASKET" to "SEPETİ SÜRÜKLE",
        "TAP THE WORD'S COLOUR" to "KELİMENİN RENGİNE DOKUN",
        "DRAG TO MOVE" to "HAREKET İÇİN SÜRÜKLE",
        "SWAP TO MATCH 3+" to "3+ EŞLEŞTİR",
        "TAP L/R OR SWIPE" to "SOL/SAĞ DOKUN VEYA KAYDIR",
        "SWIPE TO MOVE" to "HAREKET İÇİN KAYDIR",
        "REPEAT THE PATTERN" to "DESENİ TEKRARLA",
        "TAP REVEAL · HOLD FLAG" to "DOKUN: AÇ · BASILI TUT: BAYRAK",
        "WHACK · 30 SECONDS" to "VUR · 30 SANİYE",
        "DRAG TO MOVE · AUTO-FIRE" to "SÜRÜKLE · OTO. ATEŞ",
        "TAP 1 TO 9 IN ORDER" to "1'DEN 9'A SIRAYLA DOKUN",
        "HOLD LEFT / RIGHT" to "SOL / SAĞ BASILI TUT",
        "TAP THE ANSWER" to "CEVABA DOKUN",
        "TAP WHEN GREEN" to "YEŞİLKEN DOKUN",
        "TAP ON THE BEAT" to "RİTİMDE DOKUN",
        "SLIDE TO STEER" to "YÖNLENDİRMEK İÇİN KAYDIR",
        "TAP TILE TO SLIDE" to "KAYDIRMAK İÇİN KAREYE DOKUN",
        "DRAG TO SPIN" to "DÖNDÜRMEK İÇİN SÜRÜKLE",
        "TAP TO DROP" to "BIRAKMAK İÇİN DOKUN",
        "TAP TARGETS · AVOID BOMBS" to "HEDEFE DOKUN · BOMBADAN KAÇ",
        "TAP A CELL · YOU ARE X" to "HÜCREYE DOKUN · SEN X'SİN",
        "SWIPE TO MERGE" to "BİRLEŞTİRMEK İÇİN KAYDIR",
        "TAP TO FLAP" to "KANAT ÇIRPMAK İÇİN DOKUN",

        // --- taglines ---
        "Fit & clear lines" to "Yerleştir, satır temizle",
        "Smash the wall" to "Duvarı parçala",
        "Pop the rising bubbles" to "Yükselen baloncukları patlat",
        "Scoop the coins" to "Paraları topla",
        "Read the word" to "Kelimeyi oku",
        "Weave the shards" to "Parçaların arasından geç",
        "Match 3 rush" to "3'lü eşleştirme",
        "Rhythm runner · 5 levels" to "Ritim koşusu · 5 bölüm",
        "Box in the bot" to "Botu köşeye sıkıştır",
        "Find the exit" to "Çıkışı bul",
        "Repeat the pattern" to "Deseni tekrarla",
        "Clear the board" to "Tahtayı temizle",
        "Whack in 30s" to "30 saniyede vur",
        "Bullet-heaven roguelite" to "Mermi cenneti roguelite",
        "Tap 1-9 fast" to "1-9'a hızlı dokun",
        "Spin through bars" to "Çubukların arasından dön",
        "Duel the AI" to "Yapay zekâ ile düello",
        "Solve fast" to "Hızlı çöz",
        "Wait for green" to "Yeşili bekle",
        "Tap to the beat" to "Ritme göre dokun",
        "Bounce to the sky" to "Gökyüzüne zıpla",
        "Order the tiles" to "Kareleri sırala",
        "Classic, reborn" to "Klasik, yeniden",
        "Thread the gaps" to "Boşluklardan geç",
        "Build the tower" to "Kuleyi inşa et",
        "Pop the targets" to "Hedefleri patlat",
        "Beat the AI" to "Yapay zekâyı yen",
        "Merge to 2048" to "2048'e birleştir",
        "Flap through gates" to "Kapılardan geç",
        "Tap out a tune" to "Bir ezgi çal",
        "Draw on your wrist" to "Bileğine çiz",
        "Stopwatch & timer" to "Kronometre ve zamanlayıcı",

        // --- in-game HUD / overlays ---
        "LEVEL" to "SEVİYE",
        "LV" to "SV",
        "ROUND" to "RAUNT",
        "ROUND WON" to "RAUNT KAZANILDI",
        "WINS" to "GALİBİYET",
        "STREAK" to "SERİ",
        "WIN!" to "KAZANDIN!",
        "MOVES" to "HAMLE",
        "SOLVED" to "ÇÖZÜLDÜ",
        "NEXT" to "SONRAKİ",
        "PERFECT" to "MÜKEMMEL",
        "GET READY" to "HAZIR OL",
        "WAIT" to "BEKLE",
        "TAP!" to "DOKUN!",
        "TOO SOON" to "ÇOK ERKEN",
        "COMPLETE" to "TAMAMLANDI",
        "ATTEMPT" to "DENEME",
        "attempts" to "deneme",
        "TAP TO START" to "BAŞLAMAK İÇİN DOKUN",
        "TAP / HOLD TO JUMP" to "DOKUN / BASILI TUT: ZIPLA",
        "CONTINUE" to "DEVAM ET",
        "TIME ATTACK" to "ZAMANA KARŞI",
        "ENDLESS" to "SONSUZ",
        "MODE" to "MOD",
        "GOOD" to "İYİ",
        "MISS" to "KAÇTI",
        "WATCH" to "İZLE",
        "GO" to "HADİ",

        // --- colour words (Color Match / Stroop) ---
        "RED" to "KIRMIZI",
        "GREEN" to "YEŞİL",
        "BLUE" to "MAVİ",
        "AMBER" to "SARI",
        "PURPLE" to "MOR",
        "CYAN" to "TURKUAZ",

        // --- tools ---
        "Gallery" to "Galeri",
        "No saved drawings" to "Kayıtlı çizim yok",
        "Draw, then Save" to "Çiz, sonra Kaydet",
        "Tap to edit" to "Düzenlemek için dokun",
        "Delete?" to "Silinsin mi?",
        "STOPWATCH" to "KRONOMETRE",
        "TIMER" to "ZAMANLAYICI",
        "RUNNING" to "ÇALIŞIYOR",
        "STOP" to "DUR",
        "RESET" to "SIFIRLA",
        "SET TIME ↻" to "SÜRE GİR ↻",
        "TIME!" to "SÜRE!",
        "SAVED ✓" to "KAYDEDİLDİ ✓",
        "SAVE FAILED" to "KAYDEDİLEMEDİ",
        "REC" to "KAYIT",
        "LOOP" to "DÖNGÜ",

        // --- new categories ---
        "Action" to "Aksiyon",
        "Strategy" to "Strateji",

        // --- new games: control hints ---
        "TAP THE TARGETS" to "HEDEFLERE DOKUN",
        "FLIP TWO TO MATCH" to "EŞLEŞTİRMEK İÇİN İKİ KART AÇ",
        "TAP TO TOGGLE LIGHTS" to "IŞIKLARI DEĞİŞTİR",
        "SWIPE TO SLICE" to "KESMEK İÇİN KAYDIR",
        "TAP TO HOP UP" to "ZIPLAMAK İÇİN DOKUN",
        "TAP THE ODD TILE" to "FARKLI KAREYE DOKUN",

        // --- new games: taglines ---
        "Tap on the dot" to "Noktaya dokun",
        "Match the symbols" to "Sembolleri eşle",
        "Turn them all off" to "Hepsini söndür",
        "Swipe to slice" to "Kesmek için kaydır",
        "Hop through traffic" to "Trafikten geç",
        "Spot the difference" to "Farkı bul",

        // ===== 60-content expansion =====
        // --- taglines ---
        "Tap the dark tiles" to "Karanlık karelere dokun",
        "Hit the colour" to "Renge vur",
        "Tap on the arc" to "Yaya dokun",
        "Stop on the mark" to "İşarette durdur",
        "Flick the arrows" to "Okları kaydır",
        "Clear the invaders" to "İstilacıları temizle",
        "Hold to fly" to "Uçmak için basılı tut",
        "Defend the base" to "Üssü savun",
        "Smack the puck" to "Diski vur",
        "Hold to climb" to "Tırmanmak için basılı tut",
        "Weave the lanes" to "Şeritlerde süzül",
        "Block the storm" to "Fırtınayı engelle",
        "Drop the chips" to "Çipleri bırak",
        "Pop bubble clusters" to "Baloncuk gruplarını patlat",
        "Keep it alive" to "Topu düşürme",
        "Steer the corridor" to "Koridorda yönlendir",
        "Match the gate" to "Kapıyı eşle",
        "Spin to the gap" to "Boşluğa döndür",
        "Thread the gates" to "Kapılardan geç",
        "Fire the pins" to "İğneleri at",
        "Chop the trunk" to "Gövdeyi kes",
        "Crates on targets" to "Kutuları hedefe koy",
        "Connect the dots" to "Noktaları birleştir",
        "Seal the network" to "Ağı tamamla",
        "Free the red block" to "Kırmızı bloğu kurtar",
        "Move the tower" to "Kuleyi taşı",
        "Paint by numbers" to "Sayılarla boya",
        "Fill the grid" to "Izgarayı doldur",
        "Leave one peg" to "Tek taş bırak",
        "Clear the lines" to "Satırları temizle",
        "Sort the colours" to "Renkleri ayır",
        "Find the ball" to "Topu bul",
        "Crack the code" to "Şifreyi çöz",
        "Recall the digits" to "Rakamları hatırla",
        "Find them in order" to "Sırayla bul",
        "Recall the pattern" to "Deseni hatırla",
        "Unscramble the word" to "Kelimeyi çöz",
        "Add up to ten" to "On'a tamamla",
        "How many dots?" to "Kaç nokta var?",
        "Flip and flank" to "Çevir ve kuşat",
        "Four in a row" to "Dört taş",
        "Close more boxes" to "Daha çok kutu kap",
        "Five in a row" to "Beş taş",
        "Take the last stick" to "Son çöpü al",
        "Beat the dealer" to "Krupiyeyi yen",
        "Call the card" to "Kartı tahmin et",
        "Press your luck" to "Şansını dene",
        "Read the AI" to "Yapay zekâyı oku",
        "Roll the best hand" to "En iyi eli at",
        "Roll the dice" to "Zar at",
        "Heads or tails" to "Yazı mı tura mı",
        "Spin to decide" to "Döndür ve karar ver",
        "Count anything" to "Her şeyi say",
        "Keep the tempo" to "Tempoyu tut",
        "Calm your mind" to "Zihnini sakinleştir",
        "Find true level" to "Tam dengeyi bul",
        "Find your bearing" to "Yönünü bul",
        "Light it up" to "Aydınlat",
        "Time around the globe" to "Dünyada saat",

        // --- control hints ---
        "TAP THE DARK TILE" to "KARANLIK KAREYE DOKUN",
        "TAP THE MATCHING COLOUR" to "EŞLEŞEN RENGE DOKUN",
        "TAP WHEN THE DOT HITS THE ARC" to "NOKTA YAYA GELİNCE DOKUN",
        "TAP TO STOP IN THE ZONE" to "BÖLGEDE DURDURMAK İÇİN DOKUN",
        "FLICK THE ARROW'S WAY" to "OKUN YÖNÜNE KAYDIR",
        "HOLD TO FLY" to "UÇMAK İÇİN BASILI TUT",
        "TAP TO INTERCEPT" to "ENGELLEMEK İÇİN DOKUN",
        "DRAG YOUR MALLET" to "RAKETİNİ SÜRÜKLE",
        "HOLD TO CLIMB" to "TIRMANMAK İÇİN BASILI TUT",
        "SWIPE TO SWITCH LANES" to "ŞERİT DEĞİŞTİRMEK İÇİN KAYDIR",
        "DRAG TO AIM THE SHIELD" to "KALKANI YÖNLENDİRMEK İÇİN SÜRÜKLE",
        "TAP TO DROP A CHIP" to "ÇİP BIRAKMAK İÇİN DOKUN",
        "DRAG TO AIM · RELEASE TO FIRE" to "SÜRÜKLE NİŞAN AL · BIRAK ATEŞLE",
        "TAP LEFT / RIGHT TO FLIP" to "SOL/SAĞ DOKUN: RAKET",
        "DRAG TO STEER" to "YÖNLENDİRMEK İÇİN SÜRÜKLE",
        "TAP TO CYCLE COLOUR" to "RENGİ DEĞİŞTİRMEK İÇİN DOKUN",
        "HOLD LEFT / RIGHT TO SPIN" to "DÖNMEK İÇİN SOL/SAĞ BASILI TUT",
        "DRAG TO LINE UP THE GAP" to "BOŞLUĞA HİZALAMAK İÇİN SÜRÜKLE",
        "TAP TO FIRE A PIN" to "İĞNE ATMAK İÇİN DOKUN",
        "TAP THE SAFE SIDE" to "GÜVENLİ TARAFA DOKUN",
        "SWIPE TO PUSH · TAP UNDO" to "İTMEK İÇİN KAYDIR · GERİ AL: DOKUN",
        "DRAG TO CONNECT THE DOTS" to "NOKTALARI BAĞLAMAK İÇİN SÜRÜKLE",
        "TAP TILE TO ROTATE" to "DÖNDÜRMEK İÇİN KAREYE DOKUN",
        "DRAG BLOCKS TO FREE RED" to "KIRMIZIYI KURTARMAK İÇİN BLOKLARI SÜRÜKLE",
        "TAP PEG TO LIFT · TAP TO DROP" to "KALDIR: DOKUN · BIRAK: DOKUN",
        "TAP FILL · HOLD CROSS" to "DOKUN: DOLDUR · BASILI TUT: ÇARPI",
        "TAP CELL · TAP NUMBER" to "HÜCREYE DOKUN · SAYIYA DOKUN",
        "TAP PEG · TAP HOLE TO JUMP" to "TAŞA DOKUN · BOŞLUĞA DOKUN: ATLA",
        "TAP MOVE · SWIPE UP ROTATE · DOWN DROP" to "DOKUN: KAYDIR · YUKARI: DÖNDÜR · AŞAĞI: BIRAK",
        "TAP TUBE · TAP TO POUR" to "TÜPE DOKUN · DÖKMEK İÇİN DOKUN",
        "FOLLOW THE BALL · TAP A CUP" to "TOPU TAKİP ET · BARDAĞA DOKUN",
        "TAP SLOTS · CRACK THE CODE" to "YUVALARA DOKUN · ŞİFREYİ ÇÖZ",
        "WATCH THEN KEY IT BACK" to "İZLE SONRA GİR",
        "TAP 1, 2, 3 … IN ORDER" to "1, 2, 3 … SIRAYLA DOKUN",
        "MEMORISE THEN TAP THE TILES" to "EZBERLE SONRA KARELERE DOKUN",
        "TAP LETTERS TO SPELL THE WORD" to "KELİMEYİ YAZMAK İÇİN HARFLERE DOKUN",
        "TAP TILES THAT SUM TO 10" to "TOPLAMI 10 OLAN KARELERE DOKUN",
        "COUNT THE DOTS · PICK THE NUMBER" to "NOKTALARI SAY · SAYIYI SEÇ",
        "TAP A HIGHLIGHTED SQUARE" to "VURGULU KAREYE DOKUN",
        "TAP A COLUMN TO DROP" to "BIRAKMAK İÇİN SÜTUNA DOKUN",
        "TAP BETWEEN DOTS TO DRAW" to "ÇİZMEK İÇİN NOKTALAR ARASINA DOKUN",
        "TAP TO PLACE A STONE" to "TAŞ KOYMAK İÇİN DOKUN",
        "TAKE THE LAST STICK TO WIN" to "KAZANMAK İÇİN SON ÇÖPÜ AL",
        "HIT OR STAND · BEAT THE DEALER" to "ÇEK YA DA DUR · KRUPİYEYİ YEN",
        "HIGHER OR LOWER?" to "YÜKSEK Mİ ALÇAK MI?",
        "ROLL TO BUILD · HOLD TO BANK" to "TOPLA: AT · KASA: TUT",
        "ROCK · PAPER · SCISSORS" to "TAŞ · KÂĞIT · MAKAS",
        "HOLD DICE · REROLL · BANK" to "ZAR TUT · YENİDEN AT · KASA",

        // --- in-game labels / overlays ---
        "WHICH CUP?" to "HANGİ BARDAK?",
        "LINES" to "SATIR",
        "TOPPED OUT" to "TEPEYE ULAŞTI",
        "YOU WIN" to "KAZANDIN",
        "AI WINS" to "YZ KAZANDI",
        "DRAW" to "BERABERE",
        "AI…" to "YZ…",
        "YOUR TURN" to "SIRA SENDE",
        "YOU" to "SEN",
        "AI" to "YZ",
        "DEALER" to "KRUPİYE",
        "HIT" to "ÇEK",
        "STAND" to "DUR",
        "BUST" to "BATTIN",
        "DEALER WINS" to "KRUPİYE KAZANDI",
        "PUSH" to "BERABERE",
        "HIGH" to "YÜKSEK",
        "LOW" to "ALÇAK",
        "ROLL" to "AT",
        "HOLD" to "TUT",
        "CHOOSE" to "SEÇ",
        "GUESS" to "TAHMİN ET",
        "FIVE OF A KIND" to "BEŞLİ",
        "FOUR OF A KIND" to "DÖRTLÜ",
        "FULL HOUSE" to "FULL",
        "STRAIGHT" to "SIRALI",
        "THREE OF A KIND" to "ÜÇLÜ",
        "TWO PAIR" to "İKİ ÇİFT",
        "PAIR" to "ÇİFT",
        "HIGH CARD" to "YÜKSEK KART",
        "REROLL" to "YENİDEN",
        "BANK" to "KASA",
        "COUNT!" to "SAY!",
        "TOTAL" to "TOPLAM",

        // --- tools ---
        "HEADS" to "TURA",
        "TAILS" to "YAZI",
        "TAP TO FLIP" to "ÇEVİRMEK İÇİN DOKUN",
        "BREATHE IN" to "NEFES AL",
        "BREATHE OUT" to "NEFES VER",
        "Just breathe" to "Sadece nefes al",
        "No sensor" to "Sensör yok",
        "No compass" to "Pusula yok",
        "TAP TO TURN ON" to "AÇMAK İÇİN DOKUN",
        "TAP TO TURN OFF" to "KAPATMAK İÇİN DOKUN"
    )
}

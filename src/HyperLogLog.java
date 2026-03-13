import java.util.HashSet;

public class HyperLogLog {

    //  DEĞİŞKENLER
    int b;           // kova sayısını belirleyen bit sayısı (örn: b=12 → 4096 kova)
    int m;           // toplam kova sayısı = 2^b
    int[] registers; // her kovanın tuttuğu maksimum ardışık sıfır değeri
    double alpha;    // harmonik ortalamayı düzelten sabit (makaleden alınmış)

    //KURUCU METOT
    public HyperLogLog(int b) {
        this.b = b;
        this.m = 1 << b;          // 1 << b  ==  2^b  (bit kaydırma ile üs hesabı)
        this.registers = new int[m]; // başlangıçta tüm kovalar 0

        // alpha sabiti: kova sayısına göre değişen düzeltme katsayısı
        if      (m == 16) alpha = 0.673;
        else if (m == 32) alpha = 0.697;
        else if (m == 64) alpha = 0.709;
        else              alpha = 0.7213 / (1.0 + 1.079 / m);
    }

    //  HASH FONKSİYONU
    // elemanı 64-bit sayıya çevirir, tüm bitler eşit dağılır
    private long hash(String eleman) {
        long h = eleman.hashCode();   // Java'nın hashCode'u başlangıç noktası
        h = h ^ (h >>> 33);           // XOR + kaydırma: bit karıştırma (mixing)
        h *= 0xff51afd7ed558ccdL;     // büyük sabit ile çarp: daha iyi dağılım
        h = h ^ (h >>> 33);           // tekrar karıştır
        h *= 0xc4ceb9fe1a85ec53L;     // ikinci sabit ile çarp
        h = h ^ (h >>> 33);           // son karıştırma
        return h;
    }

    //  ARDIŞIK SIFIR SAYICI
    // sayının başında kaç tane 0 biti var? (ne çok sıfır → o kadar nadir olay)
    private int ardisikSifirSay(long sayi) {
        if (sayi == 0) return 65;                          // tüm bitler 0 ise maksimum
        return Long.numberOfLeadingZeros(sayi) + 1;        // baştan sıfır say, +1 ekle
    }

    // --- ELEMAN EKLEME (BUCKETING MEKANİZMASI) ---
    public void ekle(String eleman) {
        long hashDegeri = hash(eleman);                            // hash al

        // BUCKETİNG: hash'in ilk b biti → hangi kovaya gideceğini belirler
        int kovaIndeksi = (int)(hashDegeri >>> (64 - b)) & (m - 1);

        // kova bitleri çıkarılır, kalan bitler ardışık sıfır saymak için hazırlanır
        long kalanBitler = hashDegeri << b;

        // REGISTER GÜNCELLEME: bu kovadaki maksimum ardışık sıfır değeri saklanır
        int sifirSayisi = ardisikSifirSay(kalanBitler);
        if (sifirSayisi > registers[kovaIndeksi]) {
            registers[kovaIndeksi] = sifirSayisi;  // sadece büyük olan tutulur
        }
    }

    // KARDİNALİTE TAHMİNİ
    public int tahminEt() {

        // HARMONİK ORTALAMA: Σ( 2^(-registers[j]) ) toplamı hesaplanır
        double toplamPayda = 0.0;
        for (int j = 0; j < m; j++) {
            toplamPayda += Math.pow(2.0, -registers[j]);
        }

        // ANA FORMÜL (RAW ESTİMATE): E = alpha × m² / Σ(2^(-r[j]))
        double E = alpha * m * m / toplamPayda;

        // KÜÇÜK VERİ DÜZELTMESİ (Linear Counting): E < 2.5×m ise ve boş kova varsa
        if (E < 2.5 * m) {
            int bosKova = 0;
            for (int r : registers) {
                if (r == 0) bosKova++;   // henüz hiç eleman görmemiş kovalar
            }
            if (bosKova > 0) {
                E = m * Math.log((double) m / bosKova);  // LC formülü: m × ln(m/V)
            }
        }

        // BÜYÜK VERİ DÜZELTMESİ: E > 2^32/30 ise hash çakışması başlar
        double ikiNin32Si = Math.pow(2, 32);
        if (E > ikiNin32Si / 30.0) {
            E = -ikiNin32Si * Math.log(1.0 - E / ikiNin32Si);  // çakışma düzeltmesi
        }

        return (int) E;
    }

    //  BİRLEŞTİRME (MERGE / UNION)
    // iki HLL'i veri kaybı olmadan tek HLL'e dönüştürür
    public HyperLogLog birlestir(HyperLogLog diger) {
        if (this.b != diger.b) {
            throw new IllegalArgumentException("b degerleri esit olmali!");
        }
        HyperLogLog sonuc = new HyperLogLog(this.b);
        for (int i = 0; i < m; i++) {
            sonuc.registers[i] = Math.max(this.registers[i], diger.registers[i]); // her kovada büyük olanı al
        }
        return sonuc;
    }


    public static void main(String[] args) {

        System.out.println("=".repeat(55));
        System.out.println("  HyperLogLog - Kardinalite Tahmini");
        System.out.println("=".repeat(55));

        // TEST 1: temel doğruluk — farklı boyutlarda kaç hata yapıyor?
        System.out.println("\n--- TEST 1: Temel Dogruluk (b=12) ---");
        HyperLogLog hll = new HyperLogLog(12);
        int[] testSayilari = {100, 1_000, 10_000, 100_000, 1_000_000};
        System.out.printf("%-15s %-15s %-10s%n", "Gercek", "Tahmin", "Hata");
        System.out.println("-".repeat(42));
        for (int hedef : testSayilari) {
            hll.registers = new int[hll.m];                          // HLL'i sıfırla
            for (int i = 0; i < hedef; i++) hll.ekle("e" + i);      // eleman ekle
            int tahmin = hll.tahminEt();
            double hata = Math.abs((double)(tahmin - hedef) / hedef) * 100;
            System.out.printf("%-15d %-15d %.2f%%%n", hedef, tahmin, hata);
        }

        // TEST 2: sigma formülü — m arttıkça hata nasıl azalıyor?
        System.out.println("\n--- TEST 2: Kova Sayisi ve Hata Iliskisi ---");
        System.out.println("Formul: sigma = 1.04 / sqrt(m)");
        System.out.printf("%-5s %-8s %-15s %-20s%n", "b", "m", "Teorik Hata", "Bellek");
        System.out.println("-".repeat(50));
        for (int bi = 4; bi <= 14; bi += 2) {
            int mi = 1 << bi;
            double sigma = 1.04 / Math.sqrt(mi) * 100;  // SIGMA FORMÜLÜ
            System.out.printf("%-5d %-8d %.2f%%           %d int = %d byte%n",
                    bi, mi, sigma, mi, mi * 4);
        }
        System.out.println(">> m 4 katina cikinca hata yariya iner.");

        // TEST 3: merge — iki sunucudaki HLL birleşince sonuç doğru mu?
        System.out.println("\n--- TEST 3: Birlestirme (Merge) ---");
        HyperLogLog sunucuA = new HyperLogLog(12);
        HyperLogLog sunucuB = new HyperLogLog(12);
        for (int i = 0;       i < 500_000; i++) sunucuA.ekle("kullanici_" + i);
        for (int i = 300_000; i < 800_000; i++) sunucuB.ekle("kullanici_" + i);
        HyperLogLog birlesik = sunucuA.birlestir(sunucuB);
        System.out.println("Sunucu A tahmini (gercek 500.000): " + sunucuA.tahminEt());
        System.out.println("Sunucu B tahmini (gercek 500.000): " + sunucuB.tahminEt());
        System.out.println("Birlesik tahmin  (gercek 800.000): " + birlesik.tahminEt());
        double birlesimHata = Math.abs((double)(birlesik.tahminEt() - 800_000) / 800_000) * 100;
        System.out.printf("Birlesim hatasi: %.2f%%%n", birlesimHata);

        // TEST 4: bellek — HLL ne kadar az yer tutuyor?
        System.out.println("\n--- TEST 4: Bellek Karsilastirmasi ---");
        System.out.printf("%-10s %-18s %-15s %-12s%n", "Eleman", "HashSet", "HLL (b=12)", "Tasarruf");
        System.out.println("-".repeat(58));
        int[] miktarlar = {1_000, 100_000, 1_000_000, 10_000_000};
        long hllBellek = 4096L * 4;  // 4096 kova × 4 byte = 16 KB (sabit!)
        for (int n : miktarlar) {
            long hashsetBellek = (long) n * 70;  // HashSet: her eleman ~70 byte
            System.out.printf("%-10s %-18s %-15s %dx%n",
                    formatSayi(n), formatByte(hashsetBellek), formatByte(hllBellek),
                    hashsetBellek / hllBellek);
        }

        // TEST 5: birim testleri — her bileşen ayrı ayrı doğrulanıyor
        System.out.println("\n--- TEST 5: Birim Testleri ---");
        birimTestleri();
    }

    // --- BİRİM TESTLERİ ---
    static void birimTestleri() {
        int gecen = 0, toplam = 0;

        // boş HLL sıfır tahmin vermeli
        toplam++;
        if (new HyperLogLog(8).tahminEt() <= 5) {
            System.out.println("[OK] Bos HLL → 0'a yakin"); gecen++;
        } else System.out.println("[FAIL] Bos HLL");

        // 1000 farklı eleman → tahmin 700-1300 arasında olmalı
        toplam++;
        HyperLogLog t = new HyperLogLog(8);
        for (int i = 0; i < 1000; i++) t.ekle("x" + i);
        int v = t.tahminEt();
        if (v >= 700 && v <= 1300) {
            System.out.println("[OK] 1000 eleman → " + v); gecen++;
        } else System.out.println("[FAIL] 1000 eleman → " + v);

        // 100K eleman → %10 tolerans
        toplam++;
        HyperLogLog t2 = new HyperLogLog(12);
        for (int i = 0; i < 100_000; i++) t2.ekle("y" + i);
        int v2 = t2.tahminEt();
        if (v2 >= 90_000 && v2 <= 110_000) {
            System.out.println("[OK] 100K eleman → " + v2); gecen++;
        } else System.out.println("[FAIL] 100K → " + v2);

        // aynı eleman 5000 kez → register değişmemeli, tahmin 1'e yakın
        toplam++;
        HyperLogLog t3 = new HyperLogLog(8);
        for (int i = 0; i < 5000; i++) t3.ekle("ayni");
        if (t3.tahminEt() <= 50) {
            System.out.println("[OK] Tekrarli eleman → " + t3.tahminEt()); gecen++;
        } else System.out.println("[FAIL] Tekrarli → " + t3.tahminEt());

        // farklı b değerli iki HLL birleştirilemez → hata fırlatmalı
        toplam++;
        try {
            new HyperLogLog(8).birlestir(new HyperLogLog(12));
            System.out.println("[FAIL] Hata bekleniyor");
        } catch (IllegalArgumentException e) {
            System.out.println("[OK] Farkli b → IllegalArgumentException"); gecen++;
        }

        // birleştirme doğruluğu: 10K + 10K, 5K ortak → 15K unique
        toplam++;
        HyperLogLog a = new HyperLogLog(12), b2 = new HyperLogLog(12);
        for (int i = 0;     i < 10_000; i++) a.ekle("z" + i);
        for (int i = 5_000; i < 15_000; i++) b2.ekle("z" + i);
        int vb = a.birlestir(b2).tahminEt();
        if (vb >= 12_000 && vb <= 18_000) {
            System.out.println("[OK] Birlestirme → " + vb + " (gercek: 15.000)"); gecen++;
        } else System.out.println("[FAIL] Birlestirme → " + vb);

        // HLL ile HashSet karşılaştırması: aynı veriye bakıyorlar, sonuçlar yakın mı?
        toplam++;
        HyperLogLog hllT = new HyperLogLog(12);
        HashSet<String> set = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            String s = "elem_" + (i % 40_000);  // 40K unique eleman
            hllT.ekle(s); set.add(s);
        }
        double hata = Math.abs((double)(hllT.tahminEt() - set.size()) / set.size()) * 100;
        if (hata < 8.0) {
            System.out.printf("[OK] HashSet=%d, HLL=%d, Hata=%.2f%%%n",
                    set.size(), hllT.tahminEt(), hata); gecen++;
        } else System.out.printf("[FAIL] Hata=%.2f%%%n", hata);

        System.out.println("\nSonuc: " + gecen + "/" + toplam + " test gecti.");
    }

    // yardımcı formatlama
    static String formatSayi(int n) {
        if (n >= 1_000_000) return (n/1_000_000) + "M";
        if (n >= 1_000)     return (n/1_000) + "K";
        return String.valueOf(n);
    }

    static String formatByte(long b) {
        if (b >= 1_048_576) return String.format("%.0f MB", b/1_048_576.0);
        if (b >= 1_024)     return String.format("%.0f KB", b/1_024.0);
        return b + " B";
    }
}
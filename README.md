# HyperLogLog — Kardinalite Tahmini

Bir veri kümesindeki tekil eleman sayısını düşük bellek kullanımıyla tahmin eden HyperLogLog algoritmasının Java ile sıfırdan geliştirilmiş implementasyonudur.

---


## Genel Bakış

Klasik yöntemde tekil eleman sayısını bulmak için tüm elemanlar HashSet gibi bir yapıda tutulur. Bu yöntem kesin sonuç verir ancak büyük veri setlerinde ciddi bellek sorununa yol açar.

| Yöntem | 10 Milyon Eleman | Doğruluk |
|--------|-----------------|----------|
| HashSet | ~668 MB | %100 |
| HyperLogLog (b=12) | ~16 KB | ~%98 |

HyperLogLog bu problemi hash değerlerinin bit örüntülerini analiz ederek çözer. Tam sayım yapmak yerine istatistiksel bir tahmin üretir.

---

## Algoritma

Dört temel bileşenden oluşur.

**1. Hash Fonksiyonu**

Gelen eleman 64-bit sayıya dönüştürülür. Java'nın hashCode metodu başlangıç noktası olarak alınır; üzerine XOR ve büyük sabitlerle çarpma işlemleri (bit mixing) uygulanarak bitlerin eşit dağılması sağlanır.

**2. Bucketing**

Hash değerinin ilk b biti, elemanın hangi kovaya atanacağını belirler. b=12 seçildiğinde 4096 kova oluşur.

**3. Register Güncelleme**

Kalan bitlerdeki maksimum ardışık sıfır sayısı ilgili kovada tutulur. Aynı eleman tekrar geldiğinde register değişmez, bu sayede tekrarlar sayılmaz.

**4. Kardinalite Tahmini**

Tüm register değerleri üzerinden harmonik ortalama hesaplanır:

```
E = alpha x m^2 / toplam(2^(-register[j]))
```

Küçük veri için Linear Counting, büyük veri için 2^32 çakışma düzeltmesi uygulanır.

---

## Kurulum ve Çalıştırma

Java 8 veya üzeri yeterlidir. Harici bağımlılık yoktur.

```bash
# Derleme
javac HyperLogLog.java

# Çalıştırma
java HyperLogLog
```

---

## Kullanım

```java
// Yeni bir HyperLogLog oluştur (b=12, 4096 kova)
HyperLogLog hll = new HyperLogLog(12);

// Eleman ekle
hll.ekle("kullanici_1");
hll.ekle("kullanici_2");
hll.ekle("kullanici_1"); // tekrar — sayılmaz

// Kardinalite tahmini al
int tahmin = hll.tahminEt();
System.out.println("Tekil eleman tahmini: " + tahmin);

// İki HLL yapısını birleştir
HyperLogLog hll2 = new HyperLogLog(12);
hll2.ekle("kullanici_3");

HyperLogLog birlesik = hll.birlestir(hll2);
System.out.println("Birlesik tahmin: " + birlesik.tahminEt());
```

---

## Test Sonuçları

b=12 (4096 kova) parametresiyle gerçekleştirilen doğruluk testleri:

| Gerçek Eleman Sayısı | Tahmin | Hata Oranı |
|----------------------|--------|------------|
| 100 | 101 | %1.00 |
| 1.000 | 998 | %0.20 |
| 10.000 | 9.914 | %0.86 |
| 100.000 | 99.744 | %0.26 |
| 1.000.000 | 998.984 | %0.10 |

**Merge testi:** İki ayrı HLL yapısı birleştirildi. 800.000 gerçek unique elemana karşı 807.723 tahmin üretildi. Hata oranı %0.97.

**Bellek karşılaştırması:**

| Eleman Sayısı | HashSet | HyperLogLog | Tasarruf |
|---------------|---------|-------------|----------|
| 1.000 | 68 KB | 16 KB | 4x |
| 100.000 | 7 MB | 16 KB | 427x |
| 1.000.000 | 67 MB | 16 KB | 4.272x |
| 10.000.000 | 668 MB | 16 KB | 42.724x |

**Birim testleri:** 7/7 başarıyla geçildi.

---

## Teorik Analiz

Beklenen standart sapma formülü:

```
sigma = 1.04 / sqrt(m)
```

m (kova sayısı) arttıkça hata azalır; ancak bellek kullanımı artar.

| b | m (kova) | Teorik Hata | Bellek |
|---|----------|-------------|--------|
| 4 | 16 | %26.00 | 64 B |
| 6 | 64 | %13.00 | 256 B |
| 8 | 256 | %6.50 | 1 KB |
| 10 | 1.024 | %3.25 | 4 KB |
| 12 | 4.096 | %1.63 | 16 KB |
| 14 | 16.384 | %0.81 | 64 KB |

Temel kural: m değeri 4 katına çıkarıldığında hata yarıya iner, bellek 2 katına çıkar.

---

## Proje Yapısı

```
HyperLogLog.java
├── HyperLogLog(int b)       — kurucu metot
├── hash(String eleman)      — 64-bit hash üretimi
├── ardisikSifirSay(long)    — leading zeros sayacı
├── ekle(String eleman)      — bucketing ve register güncelleme
├── tahminEt()               — kardinalite tahmini
├── birlestir(HyperLogLog)   — merge / union işlemi
└── main(String[] args)      — 5 test senaryosu
```

---


## Kaynak

Philippe Flajolet, Eric Fusy, Olivier Gandouet, Frederic Meunier.
"HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm." (2007)

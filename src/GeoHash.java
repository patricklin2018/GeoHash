
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: GeoHash 实现
 * @Author: 林辉煌 huihuang.lin@luckincoffee.com
 * @Date: 2019.5.18 22:19
 */
public class GeoHash {

    /**
     * 经度最小值
     */
    private static final double MIN_LONGTITUDE = -180.0;

    /**
     * 经度最大值
     */
    private static final double MAX_LONGTITUDE = 180.0;

    /**
     * 纬度最小值
     */
    private static final double MIN_LATITUDE = -90.0;

    /**
     * 纬度最大值
     */
    private static final double MAX_LATITUDE = 90.0;

    /**
     * 二进制表达最长为64位
     */
    private static final Integer MAX_BINARY_BITS = 64;

    /**
     * base32编码最长为13位，因为 5*13 = 65 > 64
     */
    private static final Integer MAX_BASE32_LENGTH = 13;

    /**
     * 每五个bit转换为base32编码
     */
    private static final int ENCODE_EVERY_BITS = 5;

    /**
     * base32 编码表
     */
    private static final char[] BASE32_LOOKUP = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    /**
     * base32 反编码对照表
     */
    private final static Map<Character, Integer> BASE32_DECODE_LOOKUP = new HashMap<>();

    /**
     * 编码 0 填充对照表
     */
    private static final String[] PADDING_LOOKUP = {"", "0", "00", "000", "0000"};

    /**
     * 方向枚举
     */
    private enum Direction {
        Top,
        Right,
        Bottom,
        Left;
    }

    /**
     * 奇数查表
     */
    private static Map<Direction, String> ODD_LOOKUP = new HashMap<>(4);

    /**
     * 偶数查表
     */
    private static Map<Direction, String> EVEN_LOOKUP = new HashMap<>(4);

    /**
     * 奇数查表边界
     */
    private static Map<Direction, String> ODD_BORDERS = new HashMap<>(4);

    /**
     * 偶数查表边界
     */
    private static Map<Direction, String> EVEN_BORDERS = new HashMap<>(4);

    /**
     * 64bit中第一位的标记
     */
    private static final long FIRST_BIT_FLAGGED = 0x8000000000000000L;

    /**
     * 静态块
     */
    static {
        for (int i = 0; i < BASE32_LOOKUP.length; i++) {
            BASE32_DECODE_LOOKUP.put(BASE32_LOOKUP[i], i);
        }
        ODD_LOOKUP.put(Direction.Top, "238967debc01fg45kmstqrwxuvhjyznp");
        ODD_LOOKUP.put(Direction.Right, "14365h7k9dcfesgujnmqp0r2twvyx8zb");
        ODD_LOOKUP.put(Direction.Bottom, "bc01fg45238967deuvhjyznpkmstqrwx");
        ODD_LOOKUP.put(Direction.Left, "p0r21436x8zb9dcf5h7kjnmqesgutwvy");
        EVEN_LOOKUP.put(Direction.Top, "14365h7k9dcfesgujnmqp0r2twvyx8zb");
        EVEN_LOOKUP.put(Direction.Right, "238967debc01fg45kmstqrwxuvhjyznp");
        EVEN_LOOKUP.put(Direction.Bottom, "p0r21436x8zb9dcf5h7kjnmqesgutwvy");
        EVEN_LOOKUP.put(Direction.Left, "bc01fg45238967deuvhjyznpkmstqrwx");
        ODD_BORDERS.put(Direction.Top, "bcfguvyz");
        ODD_BORDERS.put(Direction.Right, "prxz");
        ODD_BORDERS.put(Direction.Bottom, "0145hjnp");
        ODD_BORDERS.put(Direction.Left, "028b");
        EVEN_BORDERS.put(Direction.Top, "prxz");
        EVEN_BORDERS.put(Direction.Right, "bcfguvyz");
        EVEN_BORDERS.put(Direction.Bottom, "028b");
        EVEN_BORDERS.put(Direction.Left, "0145hjnp");
    }

    /**
     * 将经纬度转换为二进制编码
     *
     * @param lon       经度
     * @param lat       纬度
     * @param precision 精度，例如13代表每个维度上用13位二进制表示，经纬结合，共26位二进制。最长精度为32。
     * @return 二进制编码
     */
    public static String getBinary(double lon, double lat, int precision) {
        if (lon <= MIN_LONGTITUDE || lon >= MAX_LONGTITUDE) {
            throw new IllegalArgumentException(String.format("经度取值范围为(%f, %f)", MIN_LONGTITUDE, MAX_LONGTITUDE));
        }
        if (lat <= MIN_LATITUDE || lat >= MAX_LATITUDE) {
            throw new IllegalArgumentException(String.format("纬度取值范围为(%f, %f)", MIN_LATITUDE, MAX_LATITUDE));
        }
        if ((precision << 1) > MAX_BINARY_BITS) {
            throw new IllegalArgumentException("精度最长为32位");
        }

        // 经纬标识符，0代表经度，1代表纬度
        int xyFlag = 0;
        int bits = 1;
        int binaryBits = precision << 1;
        double[] lon_range = {MIN_LONGTITUDE, MAX_LONGTITUDE};
        double[] lat_range = {MIN_LATITUDE, MAX_LATITUDE};
        StringBuilder result = new StringBuilder();
        while (bits <= binaryBits) {
            char divideResult;
            if (xyFlag == 0) {
                divideResult = divideConquer(lon_range, lon);
            } else {
                divideResult = divideConquer(lat_range, lat);
            }
            result.append(divideResult);
            bits++;
            xyFlag = xyFlag ^ 0x1;
        }
        return result.toString();
    }

    /**
     * 将二进制编码转换为base32编码
     * [!] 注意，这里理应对二进制位数做出5的倍数的限制，但是为了达到不同精度都可以通过base32编码，缩短编码长度，这里不做限制，
     * 但计算出来的编码不可用于查表法，只能通过通用法，解析经纬的方式计算邻近。
     *
     * @param binary 二进制编码
     * @return base32编码
     */
    public static String getBase32(final String binary) {
        valideBinary(binary);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < binary.length(); i += ENCODE_EVERY_BITS) {
            int delta = Math.min(ENCODE_EVERY_BITS, binary.length() - i);
            String tmp = binary.substring(i, i + delta);
            int decimal = Integer.parseInt(tmp, 2);
            result.append(BASE32_LOOKUP[decimal]);
        }
        return result.toString();
    }

    /**
     * 将base32编码转换为二进制编码
     *
     * @param base32 base32编码
     * @param bits   二进制长度
     * @return 二进制编码
     */
    public static String getBinary(final String base32, int bits) {
        valideBase32(base32);
        int[] region = {(base32.length() - 1) * ENCODE_EVERY_BITS, base32.length() * ENCODE_EVERY_BITS};
        if (bits <= region[0] || bits > region[1]) {
            throw new IllegalArgumentException(String.format("精度与base32编码不匹配，长度为%d的base32编码，其二进制长度应为(%d, %d]", base32.length(), region[0], region[1]));
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < base32.length(); ++i) {
            int decimal = BASE32_DECODE_LOOKUP.get(base32.charAt(i));
            String binary = Integer.toBinaryString(decimal);
            // 需要补0的位数
            int padding = Math.min(ENCODE_EVERY_BITS - binary.length(), bits - result.length());
            binary = PADDING_LOOKUP[padding] + binary;
            result.append(binary);
        }
        return result.toString();
    }

    /**
     * 查表法，获取以当前base32编码块形成的九宫格，顺序按照编码顺序，如下：
     * 0 1 2
     * 3 4 5
     * 6 7 8
     * [!] 注意，查表法仅适用于二进制长度为5的倍数。非5倍数的请用通用法。
     *
     * @param base32 base32编码
     * @return base32编码的数组
     */
    public static String[] getNeighborsByTable(final String base32) {
        valideBase32(base32);
        String[] result = new String[9];
        result[4] = base32;
        result[1] = getNeighborWithDirectionByTable(base32, Direction.Top);
        result[0] = getNeighborWithDirectionByTable(result[1], Direction.Left);
        result[2] = getNeighborWithDirectionByTable(result[1], Direction.Right);
        result[5] = getNeighborWithDirectionByTable(base32, Direction.Right);
        result[7] = getNeighborWithDirectionByTable(base32, Direction.Bottom);
        result[3] = getNeighborWithDirectionByTable(base32, Direction.Left);
        result[6] = getNeighborWithDirectionByTable(result[7], Direction.Left);
        result[8] = getNeighborWithDirectionByTable(result[7], Direction.Right);
        return result;
    }

    /**
     * 查表法，根据输入的base32编码和方向，获取相应方向相邻的base32编码
     *
     * @param base32 base32编码
     * @param dire   方向
     * @return 相应方向相邻的base32编码
     */
    public static String getNeighborWithDirectionByTable(final String base32, Direction dire) {
        valideBase32(base32);
        boolean isOdd = (base32.length() & 0x1) == 0x1;
        String prefix = base32.substring(0, base32.length() - 1);
        char lastChar = base32.charAt(base32.length() - 1);
        // 是否处于边界
        boolean inBorder;
        char postfix;
        if (isOdd) {
            inBorder = ODD_BORDERS.get(dire).indexOf(lastChar) != -1;
            postfix = ODD_LOOKUP.get(dire).charAt(BASE32_DECODE_LOOKUP.get(lastChar));
        } else {
            inBorder = EVEN_BORDERS.get(dire).indexOf(lastChar) != -1;
            postfix = EVEN_LOOKUP.get(dire).charAt(BASE32_DECODE_LOOKUP.get(lastChar));
        }
        // 若处于边界，继续往下递归
        if (inBorder) {
            prefix = getNeighborWithDirectionByTable(prefix, dire);
        }
        String result = prefix + postfix;
        return result;
    }

    /**
     * 通用法，根据输入的base32编码和方向，获取相应方向相邻的base32编码
     *
     * @param binary 二进制编码
     * @param dire   方向
     * @return 相应方向相邻的base32编码
     */
    public static String getNeighborWithDirection(final String binary, final Direction dire) {
        if (binary.length() <= 1) {
            throw new IllegalArgumentException("二进制编码长度至少为2");
        }
        String result = binary;
        String lat, lon;
        int decimal;
        switch (dire) {
            case Top:
                lat = extractEveryTwoBit(binary, 1);
                decimal = Integer.parseInt(lat, 2);
                decimal += 1;
                lat = maskLastNBit(decimal, lat.length());
                result = integrateEveryTwoBit(result, 1, lat);
                break;
            case Right:
                lon = extractEveryTwoBit(binary, 0);
                decimal = Integer.parseInt(lon, 2);
                decimal += 1;
                lon = maskLastNBit(decimal, lon.length());
                result = integrateEveryTwoBit(result, 0, lon);
                break;
            case Bottom:
                lat = extractEveryTwoBit(binary, 1);
                decimal = Integer.parseInt(lat, 2);
                decimal -= 1;
                lat = maskLastNBit(decimal, lat.length());
                result = integrateEveryTwoBit(result, 1, lat);
                break;
            case Left:
                lon = extractEveryTwoBit(binary, 0);
                decimal = Integer.parseInt(lon, 2);
                decimal -= 1;
                lon = maskLastNBit(decimal, lon.length());
                result = integrateEveryTwoBit(result, 0, lon);
                break;
        }
        return result;
    }

    /**
     * 通用法，根据二进制编码，计算邻近九宫格
     * 0 1 2
     * 3 4 5
     * 6 7 8

     * @param binary 二进制编码
     * @return 相应方向相邻的二进制编码
     */
    public static String[] getNeighbors(final String binary) {
        valideBinary(binary);
        String[] result = new String[9];
        result[4] = binary;
        result[1] = getNeighborWithDirection(binary, Direction.Top);
        result[0] = getNeighborWithDirection(result[1], Direction.Left);
        result[2] = getNeighborWithDirection(result[1], Direction.Right);
        result[5] = getNeighborWithDirection(binary, Direction.Right);
        result[7] = getNeighborWithDirection(binary, Direction.Bottom);
        result[3] = getNeighborWithDirection(binary, Direction.Left);
        result[6] = getNeighborWithDirection(result[7], Direction.Left);
        result[8] = getNeighborWithDirection(result[7], Direction.Right);
        return result;
    }

    /**
     * 取decimal中最后n位
     *
     * @param decimal 十进制
     * @param n       最后位数
     * @return 最后n位的字符串
     */
    private static String maskLastNBit(long decimal, int n) {
        long mask = 0xffffffffffffffffL;
        decimal <<= (MAX_BINARY_BITS - n);
        long nBit = decimal & mask;
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < n; ++i, nBit <<= 1) {
            if ((nBit & FIRST_BIT_FLAGGED) == FIRST_BIT_FLAGGED) {
                res.append('1');
            } else {
                res.append('0');
            }
        }
        return res.toString();
    }

    /**
     * 以索引start为起始，提取二进制编码binary中每隔一位的值
     *
     * @param binary 二进制编码
     * @param start  起始下标
     * @return 二进制编码binary中每隔一位的值
     */
    private static String extractEveryTwoBit(String binary, int start) {
        if (start >= binary.length()) {
            throw new IllegalArgumentException("起始下表超出长度界限");
        }
        StringBuilder res = new StringBuilder();
        for (int i = start; i < binary.length(); i += 2) {
            res.append(binary.charAt(i));
        }
        return res.toString();
    }

    /**
     * 以索引start为起始，将integrate值设置进二进制编码binary中每隔一位的值
     *
     * @param binary    二进制编码
     * @param start     起始下标
     * @param integrate 待整合的编码
     * @return 整合后的二进制编码
     */
    private static String integrateEveryTwoBit(String binary, int start, String integrate) {
        if (start >= binary.length()) {
            throw new IllegalArgumentException("起始下表超出长度界限");
        }
        StringBuilder res = new StringBuilder(binary);
        for (int i = start, j = 0; i < binary.length() && j < integrate.length(); i += 2, ++j) {
            res.replace(i, i + 1, integrate.substring(j, j + 1));
        }
        return res.toString();
    }

    /**
     * 二分，根据区间，计算中间值，大于给定值则给0，小于给定值则给1
     *
     * @param range  区间
     * @param target 给定值
     * @return 大于给定值则给0，小于给定值则给1
     */
    private static char divideConquer(double[] range, double target) {
        // 防止越界
        double mid = (range[1] - range[0]) / 2 + range[0];
        if (target < mid) {
            range[1] = mid;
            return '0';
        } else {
            range[0] = mid;
            return '1';
        }
    }

    /**
     * 验证base32编码，若验证失败则抛出异常
     *
     * @param base32 base32编码
     */
    private static void valideBase32(String base32) throws IllegalArgumentException {
        if (base32 == null || base32.length() == 0) {
            throw new IllegalArgumentException("base32编码不能为空");
        }
        if (base32.length() > MAX_BASE32_LENGTH) {
            throw new IllegalArgumentException("base32编码最长为13位");
        }
    }

    /**
     * 验证base32编码，若验证失败则抛出异常
     *
     * @param binary base32编码
     */
    private static void valideBinary(String binary) throws IllegalArgumentException {
        if (binary == null || binary.length() == 0) {
            throw new IllegalArgumentException("二进制编码不能为空");
        }
        if (binary.length() > MAX_BINARY_BITS) {
            throw new IllegalArgumentException("二进制编码最长为64位");
        }
    }

}

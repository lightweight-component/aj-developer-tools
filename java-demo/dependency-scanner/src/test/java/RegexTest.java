import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 手动验证版本区间正则能否编译的演示程序。
 * <p>不是 JUnit 测试，没有匹配行为断言。两个示例正则当前相同；
 * 成功打印编译信息，失败捕获并打印语法错误位置。
 */
public class RegexTest {
    /**
     * 仅验证两个正则能否编译，语法错误打印诊断，不提供匹配断言或失败退出码。
     *
     * @param args 命令行参数；启动类交给 Spring Boot，正则演示不使用
     */
    public static void main(String[] args) {
        try {
            // 测试当前的正则表达式
            String regex = "^(\\[|\\()?([^,\\[\\]\\(\\)]*)?(?:,([^,\\[\\]\\(\\)]*))?(\\]|\\))?$";
            Pattern pattern = Pattern.compile(regex);
            System.out.println("正则表达式编译成功: " + regex);

            // 修复后的正则表达式
            String fixedRegex = "^(\\[|\\()?([^,\\[\\]\\(\\)]*)?(?:,([^,\\[\\]\\(\\)]*))?(\\]|\\))?$";
            Pattern fixedPattern = Pattern.compile(fixedRegex);
            System.out.println("修复后的正则表达式编译成功: " + fixedRegex);

        } catch (PatternSyntaxException e) {
            System.err.println("正则表达式语法错误: " + e.getMessage());
            System.err.println("错误位置: " + e.getIndex());
            System.err.println("模式: " + e.getPattern());
        }
    }
}
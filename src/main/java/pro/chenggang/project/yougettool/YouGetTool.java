package pro.chenggang.project.yougettool;

import cn.hutool.core.util.ArrayUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * @author: chenggang
 * @date 8/27/21.
 */
@Getter
@Setter
@Command(name = "you-get-tool",
        mixinStandardHelpOptions = true,
        version = "you-get-tool 1.1.0",
        description = "Auto select video container and quality when using 'you-get'")
public class YouGetTool implements Callable<Integer> {

    @Option(names = {"-u", "--url"}, description = "original download url")
    private String[] urls;

    @Option(names = {"-p", "--playlist"}, description = "original download playlist")
    private String[] playlist;

    @Option(names = {"-o", "--output"}, description = "out put dir (have to be a absolutely dir path),default is current execution dir")
    private String outputDir;

    @Option(names = {"-q", "--quality"}, description = "video quality , 720P/1080P , auto-detected if missing ")
    private String quality;

    @Option(names = {"-d", "--debug"}, description = "you-get --debug argument enabled or not, false if missing")
    private boolean debug;

    @Override
    public Integer call() throws Exception {
        YouGetToolAction youGetToolAction = YouGetToolAction.getInstance();
        if(ArrayUtil.isAllEmpty(this.urls,this.playlist)){
            youGetToolAction.systemOutPrintln("Urls or playlist requires at least one value", YouGetToolAction.COLOR.RED);
            youGetToolAction.systemOutPrintln("you-get-tool --help for more information", YouGetToolAction.COLOR.RED);
            return 0;
        }
        boolean youGetInstalled = youGetToolAction.checkYouGetInstalled();
        if(!youGetInstalled){
            return 0;
        }
        try {
            youGetToolAction.action(this.urls,this.playlist,this.outputDir,this.quality,this.debug);
        } catch (IllegalStateException e) {
            youGetToolAction.systemOutPrintln(e.getMessage(), YouGetToolAction.COLOR.RED);
        } catch (Exception e) {
            youGetToolAction.systemOutPrintln("UnExpected Exception Occurred ,Stacktrace:"+ ExceptionUtils.getStackTrace(e), YouGetToolAction.COLOR.RED);
        }
        return 0;
    }

    public static void main(String... args) {
        System.exit(new CommandLine(new YouGetTool()).execute(args));
    }
}

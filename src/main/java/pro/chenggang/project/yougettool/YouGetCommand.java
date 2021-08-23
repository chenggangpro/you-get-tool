package pro.chenggang.project.yougettool;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.CaseInsensitiveMap;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: chenggang
 * @date 8/23/21.
 */
@ShellComponent
public class YouGetCommand {

    private static final String YOU_GET_INFOMATION_CMD_TEMPLATE = "you-get -i {}";
    private static final String YOU_GET_PLAYLIST_INFOMATION_CMD_TEMPLATE = "you-get -i --playlist {}";
    private static final String YOU_GET_DOWNLOAD_CMD_TEMPLATE = "you-get --itag={} {} -o {}";
    private final Map<String,String> qualityContainer = new CaseInsensitiveMap<>();

    public YouGetCommand() {
        qualityContainer.put("720P","1280x720");
        qualityContainer.put("1080P","1920x1080");
    }

    @ShellMethod(value = "You-get Mp4 auto quality 720P/1080P")
    public String youGetMp4(@ShellOption(value = "-u",defaultValue = "",help = "specified download url or multi urls with format : [url][url]...[url]") String paramUrl,
                            @ShellOption(value = "-p",defaultValue = "",help = "specified download playlist or multi playlist with format : [playlist][playlist]...[playlist]") String paramPlaylist,
                            @ShellOption(value = "-o",defaultValue = "",help = "output dir ,should use absolute dir path , current dir path if missing") String out,
                            @ShellOption(value = "-q",defaultValue = "",help = "mp4 quality ,720P/1080P , auto-detected if missing") String quality,
                            @ShellOption(value = "-d",help = "you-get --debug argument enabled or not, false if missing")boolean debug){
        String outputDir = RuntimeUtil.execForStr("pwd").trim();
        if(StrUtil.isNotBlank(out)){
            outputDir = out;
        }
        String qualityDetectStr = "AUTO";
        if(StrUtil.isBlank(quality) || !this.qualityContainer.containsKey(quality)){
            this.systemOutPrintln("Quality is not specified (720P/1080P)，automatically detects the best video quality with mp4 container");
        }else{
            qualityDetectStr = this.qualityContainer.get(quality);
            this.systemOutPrintln("Quality is specified " + quality + " : " + qualityDetectStr);
        }
        List<String> urlList = Stream.concat(
                Optional.ofNullable(paramUrl)
                        .filter(StrUtil::isNotBlank)
                        .filter(param -> StrUtil.containsIgnoreCase(param,"[") && StrUtil.containsIgnoreCase(param,"]"))
                        .map(param -> StrUtil.subBetweenAll(param, "[", "]"))
                        .stream()
                        .flatMap(Stream::of),
                Optional.ofNullable(paramUrl)
                        .filter(StrUtil::isNotBlank)
                        .filter(param -> !StrUtil.containsIgnoreCase(param,"[") && !StrUtil.containsIgnoreCase(param,"]"))
                        .stream()
        )
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        List<String> playlistList = Stream.concat(
                Optional.ofNullable(paramPlaylist)
                        .filter(StrUtil::isNotBlank)
                        .map(param -> StrUtil.subBetweenAll(param, "[", "]"))
                        .stream()
                        .flatMap(Stream::of),
                Optional.ofNullable(paramPlaylist)
                        .filter(StrUtil::isNotBlank)
                        .filter(param -> !StrUtil.containsIgnoreCase(param,"[") && !StrUtil.containsIgnoreCase(param,"]"))
                        .stream()
        )
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        this.printAllInformation(outputDir,qualityDetectStr.toUpperCase(),urlList,playlistList);
        String finalOutputDir = outputDir;
        String finalQualityDetectStr = qualityDetectStr;
        if(CollectionUtil.isNotEmpty(urlList)){
            this.systemOutPrintln("Starting download from paramUrl ,Quality " + qualityDetectStr);
            urlList.stream()
                    .filter(StrUtil::isNotBlank)
                    .forEach(url -> {
                        String cmd = this.downloadSimpleUrlInternal(finalOutputDir, url, finalQualityDetectStr,debug);
                        this.execute(cmd);
                    });
        }
        if(CollectionUtil.isNotEmpty(playlistList)){
            this.systemOutPrintln("Starting download from paramPlaylist ,Quality " + qualityDetectStr);
            playlistList.stream()
                    .filter(StrUtil::isNotBlank)
                    .forEach(url -> this.downloadPlaylistInternal(finalOutputDir,url,finalQualityDetectStr,debug));
        }
        return "Download Success : Url Count: " + CollectionUtil.emptyIfNull(urlList).size() + " , Playlist Count : " + CollectionUtil.emptyIfNull(playlistList).size();
    }

    private String downloadSimpleUrlInternal(String outputDir,String url,String qualityIdentifier,boolean isDebug){
        Integer itag = this.getItagFromUrl(url, qualityIdentifier);
        String downloadUrl = StrUtil.format(YOU_GET_DOWNLOAD_CMD_TEMPLATE, itag, url, outputDir);
        if(isDebug){
            downloadUrl = downloadUrl + " --debug";
        }
        this.systemOutPrintln("Download Command : " + downloadUrl);
        return downloadUrl;
    }

    private void execute(String cmd){
        Process process = RuntimeUtil.exec(cmd);
        try(BufferedReader reader = IoUtil.getReader(process.getInputStream(), StandardCharsets.UTF_8)){
            String line;
            while (process.isAlive()){
                line  = reader.readLine();
                if(line != null){
                    this.systemOutPrintln(line);
                }
            }
        }catch (IOException e) {
            ExceptionUtil.wrapAndThrow(e);
        }
        RuntimeUtil.destroy(process);
    }

    private void downloadPlaylistInternal(String outputDir,String playlist,String qualityIdentifier,boolean debug){
        List<String> lines = RuntimeUtil.execForLines(StrUtil.format(YOU_GET_PLAYLIST_INFOMATION_CMD_TEMPLATE, playlist));
        lines.forEach(this::systemOutPrintln);
        List<String> urls = lines.stream()
                .filter(line -> StrUtil.containsIgnoreCase(line, "url:"))
                .map(line -> StrUtil.subAfter(line, "url:", false))
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        this.systemOutPrintln("Detect " + urls.size() + " url from playlist " + playlist);
        urls.forEach(url -> {
            this.systemOutPrintln("Download From Url " + url);
            this.downloadSimpleUrlInternal(outputDir, url, qualityIdentifier,debug);
        });
    }

    private Integer getItagFromUrl(String url,String qualityIdentifier){
        boolean isAuto = StrUtil.equalsAnyIgnoreCase(qualityIdentifier, "AUTO");
        List<String> informationResult = RuntimeUtil.execForLines(StrUtil.format(YOU_GET_INFOMATION_CMD_TEMPLATE, url));
        List<Info> mp4InfoList = Stream.of(StrUtil.subBetweenAll(String.join("\n",informationResult), "itag", "download-with"))
                .map(item -> StrUtil.split(item, "\n"))
                .map(this::getInfo)
                .filter(info -> StrUtil.containsIgnoreCase(info.getContainer(),"mp4"))
                .filter(info -> StrUtil.isNotBlank(info.getSize()))
                .collect(Collectors.toList());
        Comparator<Info> infoComparator = (o1, o2) -> {
            String o1Between = StrUtil.subBetween(o1.getSize(), "(", ")");
            String o1SizeValue = StrUtil.subBefore(o1Between, "bytes",false);
            String o2Between = StrUtil.subBetween(o2.getSize(), "(", ")");
            String o2SizeValue = StrUtil.subBefore(o2Between, "bytes",false);
            long o1LongValue = Long.parseLong(o1SizeValue.trim());
            long o2LongValue = Long.parseLong(o2SizeValue.trim());
            return Long.compare(o1LongValue, o2LongValue);
        };
        if(CollectionUtil.isEmpty(mp4InfoList)){
            throw new IllegalStateException("Could not found matched itag for : " + url);
        }
        return mp4InfoList.stream()
                .sorted(infoComparator.reversed())
                .filter(info -> {
                    if (isAuto) {
                        return true;
                    }
                    return StrUtil.containsIgnoreCase(info.getQuality(), qualityIdentifier);
                })
                .map(Info::getItag)
                .findFirst()
                .orElseGet(() -> {
                    Info info = mp4InfoList.get(0);
                    this.systemOutPrintln("Could not found matched itag for : " + url + " , using first matched : " + info);
                    return info.getItag();
                });
    }

    private void printAllInformation(String outputDir,String quality,List<String> urls,List<String> playlists){
        this.systemOutPrintln("------------------You Get Mp4 Information");
        this.systemOutPrintln("Quality      ---> " + quality);
        this.systemOutPrintln("Output Dir   ---> " + outputDir);
        CollectionUtil.emptyIfNull(urls)
                .forEach(url -> this.systemOutPrintln("Url          ---> " + url));
        CollectionUtil.emptyIfNull(playlists)
                .forEach(playlist -> this.systemOutPrintln("Playlist     ---> " + playlist));
        this.systemOutPrintln("-----------------------------------------");
    }

    /**
     * print line
     * @param line
     */
    private void systemOutPrintln(String line){
        System.out.println(line);
    }

    private Info getInfo(List<String> lines){
        Info info = new Info();
        for (String line : lines) {
            if(StrUtil.containsIgnoreCase(line,"container")){
                String container = StrUtil.trim(StrUtil.subAfter(line, "container:", false));
                info.setContainer(container);
                continue;
            }
            if(StrUtil.containsIgnoreCase(line,"quality")){
                String quality = StrUtil.trim(StrUtil.subAfter(line, "quality:", false));
                info.setQuality(quality);
                continue;
            }
            if(StrUtil.containsIgnoreCase(line,"size")){
                String size = StrUtil.trim(StrUtil.subAfter(line, "size:", false));
                info.setSize(size);
                continue;
            }
            if(StrUtil.containsIgnoreCase(line,":")){
                String itag = StrUtil.trim(StrUtil.subAfter(line, ":", false));
                if(NumberUtil.isInteger(itag)){
                    info.setItag(Integer.parseInt(itag));
                }else {
                    byte[] bytes = itag.getBytes(StandardCharsets.UTF_8);
                    bytes = ArrayUtil.sub(bytes,4,bytes.length);
                    bytes = ArrayUtil.sub(bytes,0, bytes.length - 4);
                    info.setItag(Integer.parseInt(new String(bytes,StandardCharsets.UTF_8)));
                }
            }
        }
        return info;
    }

    @Getter
    @Setter
    @ToString
    private static class Info {

        private Integer itag;
        private String container;
        private String quality;
        private String size;
    }
}

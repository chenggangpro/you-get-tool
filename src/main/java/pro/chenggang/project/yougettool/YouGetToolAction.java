package pro.chenggang.project.yougettool;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.CaseInsensitiveMap;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import picocli.CommandLine.Help.Ansi;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * YouGetToolAction
 * @author: chenggang
 * @date 8/27/21.
 */
public class YouGetToolAction {

    private static final YouGetToolAction INSTANCE = new YouGetToolAction();

    private static final String YOU_GET_VERSION_CMD_TEMPLATE = "you-get --version";
    private static final String YOU_GET_INFOMATION_CMD_TEMPLATE = "you-get -i {}";
    private static final String YOU_GET_PLAYLIST_INFOMATION_CMD_TEMPLATE = "you-get -i --playlist {}";
    private static final String YOU_GET_BY_TAG_DOWNLOAD_CMD_TEMPLATE = "you-get --itag={} {} -o {}";
    private static final String YOU_GET_BY_FORMAT_DOWNLOAD_CMD_TEMPLATE = "you-get --format={} {} -o {}";
    private final Map<String,String> qualityContainer = new CaseInsensitiveMap<>();

    private YouGetToolAction() {
        qualityContainer.put("*","AUTO");
        qualityContainer.put("720P","1280x720");
        qualityContainer.put("1080P","1920x1080");
    }

    /**
     * get action instance
     * @return
     */
    public static YouGetToolAction getInstance(){
        return INSTANCE;
    }

    /**
     * check whether you-get installed
     * @return
     */
    public boolean checkYouGetInstalled(){
        String execResult = RuntimeUtil.execForStr(YOU_GET_VERSION_CMD_TEMPLATE);
        boolean exist = StrUtil.containsIgnoreCase(execResult, "downloader");
        if(exist){
            this.systemOutPrintln("you-get has been installed",COLOR.GREEN);
            return true;
        }
        this.systemOutPrintln("you-get hasn't been installed,please install you-get using brew",COLOR.RED);
        return false;
    }

    /**
     * action
     * @param paramUrl
     * @param paramPlaylist
     * @param out
     * @param quality
     * @param debug
     */
    public void action(String[] paramUrl, String[] paramPlaylist, String out, String quality, boolean debug){
        String outputDir = RuntimeUtil.execForStr("pwd").trim();
        if(StrUtil.isNotBlank(out)){
            outputDir = out;
        }
        String qualityDetectStr = "AUTO";
        if(StrUtil.isBlank(quality) || !this.qualityContainer.containsKey(quality)){
            this.systemOutPrintln("Quality is not specified (720P/1080P)，automatically detects the best video quality with mp4 container",COLOR.WHITE);
        }else{
            qualityDetectStr = this.qualityContainer.get(quality);
            this.systemOutPrintln("Quality is specified " + quality + " : " + qualityDetectStr,COLOR.WHITE);
        }
        List<String> urlList = Optional.ofNullable(paramUrl)
                .stream()
                .flatMap(Stream::of)
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        List<String> playlistList = Optional.ofNullable(paramPlaylist)
                .stream()
                .flatMap(Stream::of)
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        this.printAllInformation(outputDir,qualityDetectStr.toUpperCase(),urlList,playlistList);
        String finalOutputDir = outputDir;
        String finalQualityDetectStr = qualityDetectStr;
        if(CollectionUtil.isNotEmpty(urlList)){
            this.systemOutPrintln("Starting download from Url ,Quality " + qualityDetectStr,COLOR.GREEN);
            urlList.stream()
                    .filter(StrUtil::isNotBlank)
                    .forEach(url -> {
                        String cmd = this.getDownloadSimpleInternalUrl(url,finalQualityDetectStr,finalOutputDir,debug);
                        this.execute(cmd);
                    });
        }
        if(CollectionUtil.isNotEmpty(playlistList)){
            this.systemOutPrintln("Starting download from paramPlaylist ,Quality " + qualityDetectStr,COLOR.WHITE);
            playlistList.stream()
                    .filter(StrUtil::isNotBlank)
                    .forEach(playlist -> {
                        List<String> lines = RuntimeUtil.execForLines(StrUtil.format(YOU_GET_PLAYLIST_INFOMATION_CMD_TEMPLATE, playlist));
                        lines.forEach(this::systemOutPrintln);
                        List<String> urls = lines.stream()
                                .filter(line -> StrUtil.containsIgnoreCase(line, "url:"))
                                .map(line -> StrUtil.subAfter(line, "url:", false))
                                .map(StrUtil::trim)
                                .collect(Collectors.toList());
                        this.systemOutPrintln("Detect " + urls.size() + " url from playlist " + playlist,COLOR.GREEN);
                        urls.forEach(url -> {
                            this.systemOutPrintln("Playlist Download From Inner Url " + url,COLOR.WHITE);
                            String cmd = this.getDownloadSimpleInternalUrl(url,finalQualityDetectStr,finalOutputDir,debug);
                            this.execute(cmd);
                        });
                    });
        }
        String result = "Download Finished : Url Count: " + CollectionUtil.emptyIfNull(urlList).size() + " , Playlist Count : " + CollectionUtil.emptyIfNull(playlistList).size();
        this.systemOutPrintln(result,COLOR.GREEN);
    }


    /**
     * getDownloadSimpleInternalUrl
     * @param url
     * @param qualityIdentifier
     * @param outputDir
     * @param isDebug
     * @return
     */
    private String getDownloadSimpleInternalUrl(String url,String qualityIdentifier,String outputDir, boolean isDebug){
        boolean isAuto = StrUtil.equalsAnyIgnoreCase(qualityIdentifier, "AUTO");
        List<String> informationResult = RuntimeUtil.execForLines(StrUtil.format(YOU_GET_INFOMATION_CMD_TEMPLATE, url));
        String allLines = String.join("\n",informationResult);
        List<Info> mp4InfoList = Collections.emptyList();
        if(StrUtil.containsIgnoreCase(allLines,"- itag:")){
            mp4InfoList =  Stream.of(StrUtil.subBetweenAll(String.join("\n",informationResult), "itag", "download-with"))
                    .map(item -> StrUtil.split(item, "\n"))
                    .map(this::getInfo)
                    .filter(info -> StrUtil.containsIgnoreCase(info.getContainer(),"mp4"))
                    .filter(info -> StrUtil.isNotBlank(info.getSize()))
                    .collect(Collectors.toList());
        }else if(StrUtil.containsIgnoreCase(allLines,"- format:")){
            mp4InfoList = Stream.of(StrUtil.subBetweenAll(String.join("\n",informationResult), "format", "download-with"))
                    .map(item -> StrUtil.split(item, "\n"))
                    .map(this::getInfo)
                    .filter(info -> StrUtil.containsIgnoreCase(info.getContainer(),"mp4"))
                    .filter(info -> StrUtil.isNotBlank(info.getSize()))
                    .collect(Collectors.toList());
        }else {
            this.systemOutPrintln("Cloud not found displayed bash value with '- itag:' or '- format:'",COLOR.YELLOW);
        }
        if(CollectionUtil.isEmpty(mp4InfoList)){
            throw new IllegalStateException("Could not found matched itag or format  for : " + url);
        }
        Comparator<Info> infoComparator = (o1, o2) -> {
            String o1Between = StrUtil.subBetween(o1.getSize(), "(", ")");
            String o1SizeValue = StrUtil.subBefore(o1Between, "bytes",false);
            String o2Between = StrUtil.subBetween(o2.getSize(), "(", ")");
            String o2SizeValue = StrUtil.subBefore(o2Between, "bytes",false);
            long o1LongValue = Long.parseLong(o1SizeValue.trim());
            long o2LongValue = Long.parseLong(o2SizeValue.trim());
            return Long.compare(o1LongValue, o2LongValue);
        };
        List<Info> finalMp4InfoList = mp4InfoList;
        return mp4InfoList.stream()
                .sorted(infoComparator.reversed())
                .filter(info -> {
                    if (isAuto) {
                        return true;
                    }
                    return StrUtil.containsIgnoreCase(info.getQuality(), qualityIdentifier);
                })
                .findFirst()
                .map(info -> {
                    if (Objects.nonNull(info.getItag())) {
                        String downloadUrl = StrUtil.format(YOU_GET_BY_TAG_DOWNLOAD_CMD_TEMPLATE, info.getItag(), url, outputDir);
                        if (isDebug) {
                            downloadUrl = downloadUrl + " --debug";
                        }
                        this.systemOutPrintln("Prepare download url : " + downloadUrl, COLOR.WHITE);
                        return downloadUrl;
                    }
                    if (Objects.nonNull(info.getFormat())) {
                        String downloadUrl = StrUtil.format(YOU_GET_BY_FORMAT_DOWNLOAD_CMD_TEMPLATE, info.getFormat(), url, outputDir);
                        if (isDebug) {
                            downloadUrl = downloadUrl + " --debug";
                        }
                        this.systemOutPrintln("Prepare download url : " + downloadUrl, COLOR.WHITE);
                        return downloadUrl;
                    }
                    throw new IllegalStateException("Could not found matched itag or format  for : " + url);
                })
                .orElseGet(() -> {
                    Info info = finalMp4InfoList.get(0);
                    if (Objects.nonNull(info.getItag())) {
                        String downloadUrl = StrUtil.format(YOU_GET_BY_TAG_DOWNLOAD_CMD_TEMPLATE, info.getItag(), url, outputDir);
                        if (isDebug) {
                            downloadUrl = downloadUrl + " --debug";
                        }
                        this.systemOutPrintln("Prepare download url : " + downloadUrl, COLOR.WHITE);
                        return downloadUrl;
                    }
                    if (Objects.nonNull(info.getFormat())) {
                        String downloadUrl = StrUtil.format(YOU_GET_BY_FORMAT_DOWNLOAD_CMD_TEMPLATE, info.getFormat(), url, outputDir);
                        if (isDebug) {
                            downloadUrl = downloadUrl + " --debug";
                        }
                        this.systemOutPrintln("Prepare download url : " + downloadUrl, COLOR.WHITE);
                        return downloadUrl;
                    }
                    throw new IllegalStateException("Could not found matched itag or format  for : " + url);
                });
    }

    /**
     * print all information
     * @param outputDir
     * @param quality
     * @param urls
     * @param playlists
     */
    private void printAllInformation(String outputDir,String quality,List<String> urls,List<String> playlists){
        this.systemOutPrintln("------------------You Get Mp4 Information",COLOR.GREEN);
        this.systemOutPrintln("Quality      ---> " + quality,COLOR.WHITE);
        this.systemOutPrintln("Output Dir   ---> " + outputDir,COLOR.WHITE);
        CollectionUtil.emptyIfNull(urls)
                .forEach(url -> this.systemOutPrintln("Url          ---> " + url,COLOR.WHITE));
        CollectionUtil.emptyIfNull(playlists)
                .forEach(playlist -> this.systemOutPrintln("Playlist     ---> " + playlist,COLOR.WHITE));
        this.systemOutPrintln("-----------------------------------------",COLOR.GREEN);
    }

    /**
     * get info
     * @param lines
     * @return
     */
    private Info getInfo(List<String> lines){
        Info info = new Info();
        for (String line : lines) {
            if(StrUtil.containsIgnoreCase(line,"container")){
                String container = StrUtil.trim(StrUtil.subAfter(line, "container:", false));
                info.setContainer(container);
            }else if(StrUtil.containsIgnoreCase(line,"quality")){
                String quality = StrUtil.trim(StrUtil.subAfter(line, "quality:", false));
                info.setQuality(quality);
            }else if(StrUtil.containsIgnoreCase(line,"size")){
                String size = StrUtil.trim(StrUtil.subAfter(line, "size:", false));
                info.setSize(size);
            }else if(StrUtil.containsIgnoreCase(line,"format")){
                String format = StrUtil.trim(StrUtil.subAfter(line, ":", false));
                byte[] bytes = format.getBytes(StandardCharsets.UTF_8);
                bytes = ArrayUtil.sub(bytes,4,bytes.length);
                bytes = ArrayUtil.sub(bytes,0, bytes.length - 4);
                info.setFormat(new String(bytes,StandardCharsets.UTF_8));
            }else if(StrUtil.containsIgnoreCase(line,":")){
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

    /**
     * execute cmd without return
     * @param cmd
     */
    private void execute(String cmd){
        Process process = RuntimeUtil.exec(cmd);
        try(BufferedReader reader = IoUtil.getReader(process.getInputStream(), StandardCharsets.UTF_8)){
            String line;
            while (process.isAlive()){
                line  = reader.readLine();
                if(line != null){
                    boolean contains = ReUtil.contains("\\d*.\\d*\\%", line);
                    if(contains){
                        System.out.print(line + "\r");
                    }else{
                        this.systemOutPrintln(line);
                    }
                }
            }
        }catch (IOException e) {
            throw new IllegalStateException("Execute 'you-get' CMD Occur Exception : " + ExceptionUtil.getRootCauseMessage(e));
        }
        RuntimeUtil.destroy(process);
    }

    /**
     * print line
     * @param line
     */
    public void systemOutPrintln(String line){
        System.out.println(line);
    }

    /**
     * print colored line
     * @param line
     * @param color
     */
    public void systemOutPrintln(String line,COLOR color){
        String coloredLine = Ansi.AUTO.string("@|" + color.getValue() + " " + line + "|@");
        this.systemOutPrintln(coloredLine);
    }

    @Getter
    @RequiredArgsConstructor
    public enum COLOR {
        /**
         * green
         */
        GREEN("green"),
        /**
         * red
         */
        RED("red"),
        /**
         * blue
         */
        BLUE("blue"),

        /**
         * yellow
         */
        YELLOW("yellow"),

        /**
         * white
         */
        WHITE("white"),

        ;

        private final String value;
    }

    @Getter
    @Setter
    @ToString
    private static class Info {

        private Integer itag;
        private String format;
        private String container;
        private String quality;
        private String size;
    }
}

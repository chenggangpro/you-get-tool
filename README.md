# you-get-tool
you-get tool 


> tools for you-get download video/playlist with mp4 format ,support multi urls  
> package using GraalVM's native-image


```bash
Usage: you-get-tool [-dhV] [-o=<outputDir>] [-q=<quality>] [-p=<playlist>]...
                    [-u=<urls>]...
Auto select video container and quality when using 'you-get'
  -d, --debug                you-get --debug argument enabled or not, false if
                               missing
  -h, --help                 Show this help message and exit.
  -o, --output=<outputDir>   out put dir (have to be a absolutely dir path),
                               default is current execution dir
  -p, --playlist=<playlist>  original download playlist
  -q, --quality=<quality>    video quality , 720P/1080P , auto-detected if
                               missing
  -u, --url=<urls>           original download url
  -V, --version              Print version information and exit.
```
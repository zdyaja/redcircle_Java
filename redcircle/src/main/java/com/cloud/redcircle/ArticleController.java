package com.cloud.redcircle;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ArticleController {
	
	@Autowired
    JdbcTemplate jdbcTemplate;
	
    @Autowired  
    RedCircleProperties redCircleProperties;
    
    
    
    
	@RequestMapping(method = RequestMethod.POST, value = "/addArticle")
	@ResponseBody
	public String handleFileUpload(@RequestParam("mePhone") String mePhone,
									@RequestParam("content") String content,
								   @RequestParam("sourceList") MultipartFile[]  sourceList,
								   @RequestParam("thumbList") MultipartFile[] thumbList,
								   RedirectAttributes redirectAttributes) {
		if (mePhone.contains("/")) {
			redirectAttributes.addFlashAttribute("message", "Folder separators not allowed");
			return "redirect:/";
		}
		if (mePhone.contains("/")) {
			redirectAttributes.addFlashAttribute("message", "Relative pathnames not allowed");
			return "redirect:/";
		}
		
		
		StringBuffer imagesSB = new StringBuffer();


		for(int i=0; i<sourceList.length;i++) {
			MultipartFile file = sourceList[i];
			MultipartFile thumbnail = thumbList[i];
			
	        UUID uuid = UUID.randomUUID();
	        
	        imagesSB.append(uuid+"#");
	        
	        String fileName = uuid.toString() + ".png";

			if (!file.isEmpty() && !thumbnail.isEmpty()) {
				try {
					BufferedOutputStream stream = new BufferedOutputStream(
							new FileOutputStream(new File(redCircleProperties.getMopaasNFS() + "/" + fileName)));
	                FileCopyUtils.copy(file.getInputStream(), stream);
					stream.close();
					
					
					
					BufferedOutputStream stream2 = new BufferedOutputStream(
							new FileOutputStream(new File(redCircleProperties.getThumbnail() + "/" + fileName)));
	                FileCopyUtils.copy(thumbnail.getInputStream(), stream2);
					stream2.close();
					redirectAttributes.addFlashAttribute("message",
							"You successfully uploaded " + mePhone + "!");
					
					
					System.out.println("{\"success\":true, \"msg\":\"上传成功\"}");

				}
				catch (Exception e) {
					redirectAttributes.addFlashAttribute("message",
							"You failed to upload " + mePhone + " => " + e.getMessage());
					System.out.println("{\"success\":false, \"msg\":\"上传失败\"}");


				}
			}
			else {
				redirectAttributes.addFlashAttribute("message",
						"You failed to upload " + mePhone + " because the file was empty");
				System.out.println("{\"success\":false, \"msg\":\"上传失败\"}");


			}
		}
		
		String type = "10";
		if (imagesSB.length() > 0) {
			if(sourceList.length == 1) {
				type = "9";
			} else {
				type = "11";
			}
		}
		
		
		Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY) + 0);
		
//		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
		String dateStr = dateFormat.format(calendar.getTime());

		
		int result = jdbcTemplate.update("INSERT INTO t_red_article(id, content, type, images, created_at, created_by, updated_at) VALUES (?,?,?,?,?,?,?)", UUID.randomUUID().toString(), content, type, imagesSB.toString(), dateStr,  mePhone, dateStr);

		if (result>0) {
    		return "{\"success\":true, \"msg\":\"添加成功\"}";

        } else {
        	return "{\"success\":false, \"msg\":\"添加失败\"}";
        }
		
		

	}
	
	
	
	
	@RequestMapping(value="/getArticles")
	@ResponseBody
    public List<Map<String, Object>> getArticles(@RequestParam HashMap<String, Object> mePhoneMap) {
		String mePhone = (String) mePhoneMap.get("mePhone");
		String circleLevel = (String) mePhoneMap.get("circleLevel");
		String startNo = (String) mePhoneMap.get("startNo");
		
		String sql = null;
		
		if ("0".equals(circleLevel)) {
			sql = "SELECT a.*, b.name FROM t_red_article a left join t_red_user b on a.created_by = b.me_phone where a.created_by = ? order by a.created_at desc limit " + startNo + ", 10";
		} else if ("1".equals(circleLevel)) {
			sql = "select c.*, d.name from t_red_article c  left join t_red_user d on c.created_by = d.me_phone where c.created_by in (select b.friend_phone from t_red_user a left join t_red_me_friend b on a.me_phone = b.me_phone where a.me_phone = ?) order by c.created_at desc limit " + startNo + ", 10";
		} else if ("2".equals(circleLevel)) {
			sql = "select c.*, d.name from t_red_article c  left join t_red_user d on c.created_by = d.me_phone where c.created_by in (select distinct c.friend_phone from t_red_me_friend c where c.me_phone in (select b.friend_phone from t_red_user a left join t_red_me_friend b on a.me_phone = b.me_phone where a.me_phone = ?)) order by c.created_at desc limit " + startNo + ", 10";
		}
				
		List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, new Object[] { mePhone });
		for (Iterator<Map<String, Object>> iterator = results.iterator(); iterator.hasNext();) {
			Map<String, Object> map = (Map<String, Object>) iterator.next();
			String commentSQL = "select a.*, b.name as commenter_by_name, c.name as commenter_to_name from t_red_comment a left join t_red_user b on a.commenter_by = b.me_phone left join t_red_user c on a.commenter_to = c.me_phone where a.article_id =  ?";
			List<Map<String, Object>> comments = jdbcTemplate.queryForList(commentSQL, new Object[] { map.get("id") });
			map.put("comments", comments);
		}
		
		return results;
	}

}

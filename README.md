# WebHack
暴力破解“2015年公务员成绩查询网站”根据目标身份证号人员对应的成绩
基本条件：拥有待查询人员的身份证号、准考证号、姓名
必备条件：待查询人员的报考序号前三位与准号证号前三位一致，本算法才可行

文件夹说明：
1. 只有/src 下的文件是关键类
2. originpic下是下载的原始验证码
3. temppic下是切割成的众多小图片，临时作用
4. trainedpic下是筛选出来的图片库，供验证码识别使用

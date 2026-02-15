

## 返回的数据结构
一般接口 API 都有固定格式的结构，如下例子所示。

```json
{
  "success": true,
  "code": null,
  "msg": "操作成功!",
  "data": "具体的数据"
}
```
DataService 默认返回`Map<String, Object>`或者`List<Map<String, Object>>`结构，这是在控制器直接返回的，没有经过任何包装。在整合到你的系统中的话会面临这两种情况：

- 不作任何处理，交由 SpringMVC 直接返回，这时候仍会输出 JSON 但没有任何统一的结构。
- 你在 Spring 中配置统一数据类型返回，一般是`@RestControllerAdvice+ResponseBodyAdvice<Object>`所控制，这时 DataService 返回的数据结构会统一成你配置的格式。

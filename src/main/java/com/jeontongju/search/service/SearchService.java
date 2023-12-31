package com.jeontongju.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongju.search.client.WishCartServiceClient;
import com.jeontongju.search.document.Product;
import com.jeontongju.search.dto.response.*;
import com.jeontongju.search.enums.temp.ConceptTypeEnum;
import com.jeontongju.search.enums.temp.FoodTypeEnum;
import com.jeontongju.search.enums.temp.RawMaterialEnum;
import com.jeontongju.search.exception.ProductNotFoundException;
import io.github.bitbox.bitbox.dto.IsWishProductDto;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.*;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private static final String PRODUCT_INDEX = "product";
  private final RestHighLevelClient client;
  private final ObjectMapper objectMapper;
  private final WishCartServiceClient wishCartServiceClient;

  public ProductDetailsDto getProductDetails(String productId, Long memberId) {

    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    boolQuery.must(new TermQueryBuilder("productId", productId));
    boolQuery.filter(new TermQueryBuilder("isActivate", true));
    boolQuery.filter(new TermQueryBuilder("isDeleted", false));

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(boolQuery);

    SearchResponse searchResponse = search(sourceBuilder);

    Boolean isLikes = false;

    Product product =
        Arrays.stream(searchResponse.getHits().getHits())
            .map(hit -> objectMapper.convertValue(hit.getSourceAsMap(), Product.class))
            .findFirst()
            .orElseThrow(ProductNotFoundException::new);

    if (memberId != null) {
      HashMap<String, Boolean> isWishInfoDto = isWishProductClient(memberId, List.of(productId));
      isLikes = isWishInfoDto.get(productId);
    }

    return ProductDetailsDto.toDto(product, isLikes);
  }

  public Page<GetMyProductDto> getMyProduct(Long sellerId, Pageable pageable) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(QueryBuilders.termQuery("sellerId", sellerId));
    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());

    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    SearchHits hits = searchResponse.getHits();

    List<GetMyProductDto> getMyProductDtoList =
        Arrays.stream(hits.getHits())
            .map(
                hit ->
                    GetMyProductDto.toDto(
                        objectMapper.convertValue(hit.getSourceAsMap(), Product.class)))
            .collect(Collectors.toList());

    return new PageImpl<GetMyProductDto>(
        getMyProductDtoList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public Page<GetSellerOneProductDto> getSellerOneProduct(Long sellerId, Pageable pageable) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(QueryBuilders.termQuery("sellerId", sellerId));
    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());

    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    SearchHits hits = searchResponse.getHits();

    List<GetSellerOneProductDto> getSellerOneProductList =
        Arrays.stream(hits.getHits())
            .map(
                hit ->
                    GetSellerOneProductDto.toDto(
                        objectMapper.convertValue(hit.getSourceAsMap(), Product.class)))
            .collect(Collectors.toList());

    return new PageImpl<GetSellerOneProductDto>(
        getSellerOneProductList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public List<GetProductDto> getProductSellerShop(
      Long sellerId, Pageable pageable, Long consumerId) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    boolQuery.must(new TermQueryBuilder("sellerId", sellerId));
    boolQuery.filter(new TermQueryBuilder("isActivate", true));
    boolQuery.filter(new TermQueryBuilder("isDeleted", false));
    sourceBuilder.query(boolQuery);

    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);

    return getProductListByIsWish(consumerId, searchResponse);
  }

  public Page<GetProductDto> getAllProductSellerShop(
      Long sellerId, Pageable pageable, Long consumerId) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    boolQuery.must(new TermQueryBuilder("sellerId", sellerId));
    boolQuery.filter(new TermQueryBuilder("isActivate", true));
    boolQuery.filter(new TermQueryBuilder("isDeleted", false));
    sourceBuilder.query(boolQuery);

    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    List<GetProductDto> getProductDtoList = getProductListByIsWish(consumerId, searchResponse);

    return new PageImpl<GetProductDto>(
        getProductDtoList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public Page<GetProductDto> getProductByCategory(
      Long categoryId,
      Pageable pageable,
      Long memberId,
      List<RawMaterialEnum> rawMaterial,
      List<FoodTypeEnum> food,
      List<ConceptTypeEnum> concept,
      Long minPrice,
      Long maxPrice,
      Double minAlcoholDegree,
      Double maxAlcoholDegree) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("categoryId", categoryId))
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false));

    filterByTerms(
            boolQuery,
            rawMaterial.stream().map(r -> r.getValue()).collect(Collectors.toList()),
            "rawMaterial.text");

    filterByTerms(
            boolQuery, food.stream().map(f -> f.getValue()).collect(Collectors.toList()), "food.text");

    filterByTerms(
            boolQuery,
            concept.stream().map(c -> c.getValue()).collect(Collectors.toList()),
            "concept.text");

    filterByRange(boolQuery, minPrice, maxPrice, "price");
    filterByRange(boolQuery, minAlcoholDegree, maxAlcoholDegree, "alcoholDegree");

    sourceBuilder.query(boolQuery);
    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    List<GetProductDto> getProductDtoList = getProductListByIsWish(memberId, searchResponse);

    return new PageImpl<GetProductDto>(
        getProductDtoList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public Page<GetProductDto> getAllProduct(
      Pageable pageable,
      Long consumerId,
      List<RawMaterialEnum> rawMaterial,
      List<FoodTypeEnum> food,
      List<ConceptTypeEnum> concept,
      Long minPrice,
      Long maxPrice,
      Double minAlcoholDegree,
      Double maxAlcoholDegree) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.matchAllQuery())
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false));

    filterByTerms(
            boolQuery,
            rawMaterial.stream().map(r -> r.getValue()).collect(Collectors.toList()),
            "rawMaterial.text");

    filterByTerms(
            boolQuery, food.stream().map(f -> f.getValue()).collect(Collectors.toList()), "food.text");

    filterByTerms(
            boolQuery,
            concept.stream().map(c -> c.getValue()).collect(Collectors.toList()),
            "concept.text");

    filterByRange(boolQuery, minPrice, maxPrice, "price");
    filterByRange(boolQuery, minAlcoholDegree, maxAlcoholDegree, "alcoholDegree");

    sourceBuilder.query(boolQuery);
    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    List<GetProductDto> getProductDtoList = getProductListByIsWish(consumerId, searchResponse);

    return new PageImpl<GetProductDto>(
        getProductDtoList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public Page<GetProductDto> getProductBySearch(
      String query,
      Pageable pageable,
      Long consumerId,
      List<RawMaterialEnum> rawMaterial,
      List<FoodTypeEnum> food,
      List<ConceptTypeEnum> concept,
      Long minPrice,
      Long maxPrice,
      Double minAlcoholDegree,
      Double maxAlcoholDegree) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    MultiMatchQueryBuilder multiMatchQuery =
        QueryBuilders.multiMatchQuery(query, "name", "description", "rawMaterial.text")
            .field("name", 2);

    boolQuery.must(multiMatchQuery);

    boolQuery.filter(new TermQueryBuilder("isActivate", true));
    boolQuery.filter(new TermQueryBuilder("isDeleted", false));

    filterByTerms(
        boolQuery,
        rawMaterial.stream().map(r -> r.getValue()).collect(Collectors.toList()),
        "rawMaterial.text");

    filterByTerms(
        boolQuery, food.stream().map(f -> f.getValue()).collect(Collectors.toList()), "food.text");

    filterByTerms(
        boolQuery,
        concept.stream().map(c -> c.getValue()).collect(Collectors.toList()),
        "concept.text");

    filterByRange(boolQuery, minPrice, maxPrice, "price");
    filterByRange(boolQuery, minAlcoholDegree, maxAlcoholDegree, "alcoholDegree");

    sourceBuilder.query(boolQuery);

    sourceBuilder.from(pageable.getPageNumber() * pageable.getPageSize());
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    SearchResponse searchResponse = search(sourceBuilder);
    List<GetProductDto> getProductDtoList = getProductListByIsWish(consumerId, searchResponse);

    return new PageImpl<GetProductDto>(
        getProductDtoList, pageable, searchResponse.getHits().getTotalHits().value);
  }

  public List<GetProductAutoDto> getProductByAutoSearch(String query) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder mustBoolQuery =
        QueryBuilders.boolQuery()
            .should(new MatchPhrasePrefixQueryBuilder("name", query))
            .should(new FuzzyQueryBuilder("name", query).fuzziness(Fuzziness.build("0.5")));

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(mustBoolQuery)
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false));
    sourceBuilder.query(boolQuery);

    sourceBuilder.from(0);
    sourceBuilder.size(10);
    SearchResponse searchResponse = search(sourceBuilder);

    return Arrays.stream(searchResponse.getHits().getHits())
        .map(
            hit ->
                GetProductAutoDto.toDto(
                    objectMapper.convertValue(hit.getSourceAsMap(), Product.class)))
        .collect(Collectors.toList());
  }

  public List<GetMainProductDto> searchCerealCropsProduct(
      Pageable pageable, Long consumerId, String rawMaterial) {
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("rawMaterial.text", rawMaterial))
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false))
            .filter(QueryBuilders.rangeQuery("stockQuantity").gt(0));

    sourceBuilder.query(boolQuery);
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    return getMainProductListByIsWish(consumerId, search(sourceBuilder));
  }

  public GetCerealCropsProductDto getCerealCropsProduct(Pageable pageable, Long consumerId) {
    return GetCerealCropsProductDto.builder()
        .sweetPotato(
            searchCerealCropsProduct(pageable, consumerId, RawMaterialEnum.SWEET_POTATO.getValue()))
        .potato(searchCerealCropsProduct(pageable, consumerId, RawMaterialEnum.POTATO.getValue()))
        .corn(searchCerealCropsProduct(pageable, consumerId, RawMaterialEnum.CORN.getValue()))
        .build();
  }

  public List<GetMainProductDto> getProduct(Pageable pageable, Long consumerId) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.matchAllQuery())
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false))
            .filter(QueryBuilders.rangeQuery("stockQuantity").gt(0));

    sourceBuilder.query(boolQuery);
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    return getMainProductListByIsWish(consumerId, search(sourceBuilder));
  }

  public List<GetMainProductDto> getHolidayProduct(Pageable pageable, Long consumerId) {

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder boolQuery =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("concept.text", "명절"))
            .filter(QueryBuilders.termQuery("isActivate", true))
            .filter(QueryBuilders.termQuery("isDeleted", false))
            .filter(QueryBuilders.rangeQuery("stockQuantity").gt(0));

    sourceBuilder.query(boolQuery);
    sourceBuilder.size(pageable.getPageSize());
    pageable.getSort().stream()
        .forEach(
            order ->
                sourceBuilder.sort(
                    SortBuilders.fieldSort(order.getProperty())
                        .order(SortOrder.fromString(order.getDirection().name()))));

    return getMainProductListByIsWish(consumerId, search(sourceBuilder));
  }

  private void filterByTerms(BoolQueryBuilder boolQuery, List<String> terms, String fieldName) {
    if (terms.size() != 0) {
      boolQuery.filter(QueryBuilders.termsQuery(fieldName, terms));
    }
  }

  private void filterByRange(
      BoolQueryBuilder boolQuery, Long minValue, Long maxValue, String fieldName) {
    RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(fieldName);

    if (minValue != -1) {
      rangeQueryBuilder.gte(minValue);
    }
    if (maxValue != -1) {
      rangeQueryBuilder.lte(maxValue);
    }
    boolQuery.filter(rangeQueryBuilder);
  }

  private void filterByRange(
      BoolQueryBuilder boolQuery, Double minValue, Double maxValue, String fieldName) {

    RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(fieldName);

    if (minValue != -1) {
      rangeQueryBuilder.gte(minValue);
    }
    if (maxValue != -1) {
      rangeQueryBuilder.lte(maxValue);
    }
    boolQuery.filter(rangeQueryBuilder);
  }

  /** 메인 상품 목록 조회 일때, 찜 유무와 함께 */
  public List<GetMainProductDto> getMainProductListByIsWish(
      Long consumerId, SearchResponse searchResponse) {

    if (consumerId != null) {
      List<Product> productList = new ArrayList<>();
      List<String> productIds = new ArrayList<>();

      Arrays.stream(searchResponse.getHits().getHits())
          .forEach(
              hit -> {
                productList.add(objectMapper.convertValue(hit.getSourceAsMap(), Product.class));
                productIds.add(hit.getId());
              });

      HashMap<String, Boolean> isWishInfoDto = isWishProductClient(consumerId, productIds);

      return productList.stream()
          .map(
              product ->
                  GetMainProductDto.toDto(product, isWishInfoDto.get(product.getProductId())))
          .collect(Collectors.toList());

    } else {
      return Arrays.stream(searchResponse.getHits().getHits())
          .map(
              hit ->
                  GetMainProductDto.toDto(
                      objectMapper.convertValue(hit.getSourceAsMap(), Product.class), false))
          .collect(Collectors.toList());
    }
  }

  /** 상품 목록 조회 일때, 찜 유무와 함께 */
  public List<GetProductDto> getProductListByIsWish(
      Long consumerId, SearchResponse searchResponse) {

    if (consumerId != null) {
      List<Product> productList = new ArrayList<>();
      List<String> productIds = new ArrayList<>();

      Arrays.stream(searchResponse.getHits().getHits())
          .forEach(
              hit -> {
                productList.add(objectMapper.convertValue(hit.getSourceAsMap(), Product.class));
                productIds.add(hit.getId());
              });

      HashMap<String, Boolean> isWishInfoDto = isWishProductClient(consumerId, productIds);

      return productList.stream()
          .map(product -> GetProductDto.toDto(product, isWishInfoDto.get(product.getProductId())))
          .collect(Collectors.toList());

    } else {
      return Arrays.stream(searchResponse.getHits().getHits())
          .map(
              hit ->
                  GetProductDto.toDto(
                      objectMapper.convertValue(hit.getSourceAsMap(), Product.class), false))
          .collect(Collectors.toList());
    }
  }

  /** isWish feign client */
  public HashMap<String, Boolean> isWishProductClient(Long consumerId, List<String> productIds) {
    return wishCartServiceClient
        .getIsWish(IsWishProductDto.builder().consumerId(consumerId).productIds(productIds).build())
        .getData();
  }

  /** document get */
  public GetResponse getDocument(GetRequest request) {

    try {
      GetResponse response = client.get(request, RequestOptions.DEFAULT);

      if (!response.isExists()) {
        throw new ProductNotFoundException();
      }
      return response;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** document search */
  public SearchResponse search(SearchSourceBuilder searchSourceBuilder) {

    try {
      SearchRequest searchRequest = new SearchRequest(PRODUCT_INDEX);
      searchRequest.source(searchSourceBuilder);

      return client.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

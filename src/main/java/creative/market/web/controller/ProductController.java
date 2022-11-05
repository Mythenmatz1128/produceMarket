package creative.market.web.controller;

import creative.market.aop.LoginCheck;
import creative.market.aop.UserType;
import creative.market.argumentresolver.Login;
import creative.market.domain.product.Product;
import creative.market.exception.FileSaveException;
import creative.market.repository.ProductRepository;
import creative.market.repository.dto.CategoryParamDTO;
import creative.market.repository.dto.ProductSearchConditionReq;
import creative.market.repository.dto.SellerAndTotalPricePerCategoryDTO;
import creative.market.repository.order.OrderProductRepository;
import creative.market.repository.query.OrderProductQueryRepository;
import creative.market.repository.query.ProductQueryRepository;
import creative.market.service.ProductService;
import creative.market.service.dto.LoginUserDTO;
import creative.market.service.dto.RegisterProductDTO;
import creative.market.service.dto.UploadFileDTO;
import creative.market.service.query.ProductQueryService;
import creative.market.util.FileStoreUtils;
import creative.market.util.FileSubPath;
import creative.market.web.dto.*;
import creative.market.service.dto.UpdateProductFormReq;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
@Slf4j
public class ProductController {

    @Value("${images}")
    private String rootPath;
    private final ProductRepository productRepository;
    private final ProductQueryRepository productQueryRepository;
    private final OrderProductQueryRepository orderProductQueryRepository;
    private final OrderProductRepository orderProductRepository;
    private final ProductService productService;
    private final ProductQueryService productQueryService;

    @GetMapping
    public ResultRes getProductList(ProductSearchConditionReq searchCondition) {// 상품 리스트 조회

        return new ResultRes(productQueryService.productShortInfoList(searchCondition));
    }

    @GetMapping("/{productId}")
    public ResultRes getProductDetail(@PathVariable Long productId) { // 상품 상세 조회
        return new ResultRes(productQueryService.productDetailInfo(productId));
    }

    @PostMapping
    @LoginCheck(type = {UserType.SELLER})
    public ResultRes registerProduct(@Valid CreateProductReq productReq, @Login LoginUserDTO loginUserDTO) { // 상품 생성

        fileEmptyCheck(productReq.getImg()); // 저장할 파일이 하나도 없는 경우

        try {
            // 사진 저장
            UploadFileDTO sigImage = FileStoreUtils.storeFile(productReq.getSigImg(), rootPath, FileSubPath.PRODUCT_PATH);
            List<UploadFileDTO> ordinalImages = FileStoreUtils.storeFiles(productReq.getImg(), rootPath, FileSubPath.PRODUCT_PATH);

            RegisterProductDTO registerProductDTO = createRegisterProductDTO(productReq, loginUserDTO, sigImage, ordinalImages);

            productService.register(registerProductDTO);
            return new ResultRes(new MessageRes("상품 등록 성공"));
        } catch (IOException e) {
            throw new FileSaveException("파일 저장에 실패했습니다. 다시 시도해주세요");
        }

    }

    @GetMapping("/update/{productId}")
    @LoginCheck(type = {UserType.SELLER})
    public ResultRes getUpdateForm(@PathVariable Long productId, @Login LoginUserDTO loginUserDTO) { // 상품 수정 전 기존 정보 전달
        productService.sellerAccessCheck(productId, loginUserDTO.getId()); // 상품 수정 권환 확인
        return new ResultRes(productQueryService.productUpdateForm(productId));
    }

    @PostMapping("/update/{productId}")
    @LoginCheck(type = {UserType.SELLER})
    public ResultRes updateProduct(@Valid UpdateProductFormReq updateFormReq, @Login LoginUserDTO loginUserDTO, @PathVariable Long productId) { // 상품 수정

        fileEmptyCheck(updateFormReq.getImg()); // 저장할 파일이 하나도 없는 경우

        productService.update(productId, updateFormReq, loginUserDTO.getId());
        return new ResultRes(new MessageRes("상품 수정 성공"));
    }

    @GetMapping("/statistics/{productId}")
    public ResultRes totalPricePercentByCategory(@PathVariable Long productId) {

        Product product = productRepository.findById(productId).orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다."));
        CategoryParamDTO categoryParamDTO = new CategoryParamDTO(null, null, null, product.getKindGrade().getId());

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = LocalDateTime.of(endDate.getYear(), endDate.getMonth(), 1, 0, 0); // 이번달 1일부터
        int rankCount = 5; // 원형 그래프에서 보여줄 top rank 개수

        //기간별 카테고리 총 판매액
        Long totalPriceSum = orderProductRepository.findCategoryOrderTotalPriceSum(categoryParamDTO, startDate, endDate);

        // 기간별 카테고리에서 상품 판매액 top 5 정보
        List<SellerAndTotalPricePerCategoryDTO> topRankPricePercent = orderProductQueryRepository.findCategoryTopRankSellerNameAndPrice(categoryParamDTO, startDate, endDate, rankCount);
        List<PercentAndPriceRes> priceTopRankPercentRes = convertToPricePercentDTOS(topRankPricePercent, totalPriceSum, productId);

        //기간별 카테고리에서 상품 판매자 판매액 정보
        SellerAndTotalPricePerCategoryDTO SellerPricePercent = orderProductQueryRepository.findCategorySellerNameAndPrice(categoryParamDTO, startDate, endDate, product.getUser().getId());
        PercentAndPriceRes percentAndPriceRes = convertToPricePercentDTO(SellerPricePercent, totalPriceSum, productId);

        return new ResultRes(new PricePercentPieGraphPerCategoryRes(priceTopRankPercentRes, percentAndPriceRes));
    }

    @GetMapping("/main-page/latest")
    public ResultRes maiPageLatestByAllCategory() { //메인 페이지 시간순 조회
        int limit = 4;
        return new ResultRes(productQueryRepository.findProductSigImgAndIdByLatestCreatedDate(limit));
    }

    @GetMapping("/main-page/order-count")
    public ResultRes mainPageOrderCntByAllCategory() { // 메인 페이지 판매 횟수순 조회
        int limit = 4;
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusMonths(1) // 한달 전
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        log.info("startDate={}, endDate={}", startDate, endDate);
        return new ResultRes(productQueryService.productSigSrcAndIdByOrderCount(limit, startDate, endDate));
    }

    private List<PercentAndPriceRes> convertToPricePercentDTOS(List<SellerAndTotalPricePerCategoryDTO> params, Long totalPriceSum, Long productId) {
        Long totalPriceSumRemain = totalPriceSum;
        List<PercentAndPriceRes> result = new ArrayList<>();

        for (int i = 0; i < params.size(); i++) {
            SellerAndTotalPricePerCategoryDTO param = params.get(i);
            result.add(convertToPricePercentDTO(param, totalPriceSum, productId));
            totalPriceSumRemain -= param.getTotalPrice();
        }

        if (totalPriceSumRemain != 0) { // 상품 카테고리별 원형 그래프에 출력할 상품 가격 합 < 특정 카테고리 가격 합 -> 원형 그래프에 '기타'로 나타내줘야 하는 경우
            PercentAndPriceRes etc = new PercentAndPriceRes("기타", totalPriceSumRemain, getPercent(totalPriceSumRemain, totalPriceSum));
            result.add(etc);
        }

        return result;
    }

    private PercentAndPriceRes convertToPricePercentDTO(SellerAndTotalPricePerCategoryDTO param, Long totalPriceSum, Long productId) {
        String sellerName = param.getSellerName();
        Long totalPrice = param.getTotalPrice();

        if (sellerName == null) {
            Product product = productRepository.findByIdFetchJoinSeller(productId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 상품입니다."));
            sellerName = product.getUser().getName();
        }
        return new PercentAndPriceRes(sellerName, totalPrice, getPercent(totalPrice, totalPriceSum));
    }

    private float getPercent(long totalPrice, long totalPriceSum) {
        return totalPriceSum == 0 ? 0 : ((float) totalPrice / totalPriceSum) * 100;
    }

    private void fileEmptyCheck(List<MultipartFile> multipartFiles) {
        if (CollectionUtils.isEmpty(multipartFiles)) {
            throw new FileSaveException("파일은 반드시 1개 이상 추가되어야 합니다.");
        }
    }

    private RegisterProductDTO createRegisterProductDTO(CreateProductReq productReq, LoginUserDTO loginUserDTO, UploadFileDTO sigImage, List<UploadFileDTO> ordinalImages) {
        return new RegisterProductDTO(productReq.getKindGradeId(), productReq.getName(), productReq.getPrice(),
                productReq.getInfo(), loginUserDTO.getId(), sigImage, ordinalImages);
    }
}

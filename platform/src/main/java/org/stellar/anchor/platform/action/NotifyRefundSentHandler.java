package org.stellar.anchor.platform.action;

import static org.stellar.anchor.api.rpc.action.ActionMethod.NOTIFY_REFUND_SENT;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_ANCHOR;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_EXTERNAL;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_STELLAR;
import static org.stellar.anchor.api.sep.SepTransactionStatus.REFUNDED;
import static org.stellar.anchor.util.AssetHelper.getAssetCode;
import static org.stellar.anchor.util.MathHelper.decimal;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.stellar.anchor.api.exception.rpc.InvalidParamsException;
import org.stellar.anchor.api.platform.PlatformTransactionData.Kind;
import org.stellar.anchor.api.rpc.action.ActionMethod;
import org.stellar.anchor.api.rpc.action.NotifyRefundSentRequest;
import org.stellar.anchor.api.sep.AssetInfo;
import org.stellar.anchor.api.sep.SepTransactionStatus;
import org.stellar.anchor.asset.AssetService;
import org.stellar.anchor.platform.data.JdbcSep24RefundPayment;
import org.stellar.anchor.platform.data.JdbcSep24Transaction;
import org.stellar.anchor.platform.data.JdbcSepTransaction;
import org.stellar.anchor.platform.validator.RequestValidator;
import org.stellar.anchor.sep24.Sep24RefundPayment;
import org.stellar.anchor.sep24.Sep24Refunds;
import org.stellar.anchor.sep24.Sep24TransactionStore;
import org.stellar.anchor.sep31.Sep31TransactionStore;

public class NotifyRefundSentHandler extends ActionHandler<NotifyRefundSentRequest> {

  public NotifyRefundSentHandler(
      Sep24TransactionStore txn24Store,
      Sep31TransactionStore txn31Store,
      RequestValidator requestValidator,
      AssetService assetService) {
    super(txn24Store, txn31Store, requestValidator, assetService, NotifyRefundSentRequest.class);
  }

  @Override
  public ActionMethod getActionType() {
    return NOTIFY_REFUND_SENT;
  }

  @Override
  protected SepTransactionStatus getNextStatus(
      JdbcSepTransaction txn, NotifyRefundSentRequest request) throws InvalidParamsException {
    if (request.getRefund() == null
        && PENDING_ANCHOR == SepTransactionStatus.from(txn.getStatus())) {
      throw new InvalidParamsException("refund is required");
    }

    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    AssetInfo assetInfo = assetService.getAsset(getAssetCode(txn.getAmountInAsset()));
    BigDecimal totalRefunded =
        decimal(txn24.getRefunds().getAmountRefunded(), assetInfo)
            .add(decimal(request.getRefund().getAmount(), assetInfo));
    if (totalRefunded.compareTo(decimal(txn.getAmountIn(), assetInfo)) >= 0) {
      return REFUNDED;
    } else {
      return PENDING_ANCHOR;
    }
  }

  @Override
  protected Set<SepTransactionStatus> getSupportedStatuses(JdbcSepTransaction txn) {
    Set<SepTransactionStatus> supportedStatuses = new HashSet<>();

    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    switch (Kind.from(txn24.getKind())) {
      case DEPOSIT:
        if (txn24.getTransferReceivedAt() != null) {
          supportedStatuses.add(PENDING_EXTERNAL);
          supportedStatuses.add(PENDING_ANCHOR);
        }
        break;
      case WITHDRAWAL:
        supportedStatuses.add(PENDING_STELLAR);
        if (txn24.getTransferReceivedAt() != null) {
          supportedStatuses.add(PENDING_ANCHOR);
        }
        break;
    }

    return supportedStatuses;
  }

  @Override
  protected void updateTransactionWithAction(
      JdbcSepTransaction txn, NotifyRefundSentRequest request) {
    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    Sep24Refunds sep24Refunds = txn24.getRefunds();

    NotifyRefundSentRequest.Refund refund = request.getRefund();
    Sep24RefundPayment refundPayment =
        JdbcSep24RefundPayment.builder()
            .id(refund.getId())
            .amount(refund.getAmount())
            .fee(refund.getAmountFee())
            .build();

    if (sep24Refunds.getRefundPayments() == null) {
      sep24Refunds.setRefundPayments(List.of(refundPayment));
    } else {
      sep24Refunds
          .getRefundPayments()
          .removeIf(payment -> payment.getId().equals(request.getRefund().getId()));
      sep24Refunds.getRefundPayments().add(refundPayment);
    }

    AssetInfo assetInfo = assetService.getAsset(getAssetCode(txn.getAmountInAsset()));
    sep24Refunds.recalculateAmounts(assetInfo);
  }
}

package org.stellar.anchor.platform.action;

import static java.util.stream.Collectors.toSet;
import static org.stellar.anchor.api.platform.PlatformTransactionData.Sep.SEP_24;
import static org.stellar.anchor.api.rpc.action.ActionMethod.NOTIFY_TRANSACTION_EXPIRED;
import static org.stellar.anchor.api.sep.SepTransactionStatus.EXPIRED;

import java.util.Arrays;
import java.util.Set;
import org.stellar.anchor.api.platform.PlatformTransactionData.Sep;
import org.stellar.anchor.api.rpc.action.ActionMethod;
import org.stellar.anchor.api.rpc.action.NotifyTransactionExpiredRequest;
import org.stellar.anchor.api.sep.SepTransactionStatus;
import org.stellar.anchor.asset.AssetService;
import org.stellar.anchor.platform.data.JdbcSepTransaction;
import org.stellar.anchor.platform.validator.RequestValidator;
import org.stellar.anchor.sep24.Sep24TransactionStore;
import org.stellar.anchor.sep31.Sep31TransactionStore;

public class NotifyTransactionExpiredHandler
    extends ActionHandler<NotifyTransactionExpiredRequest> {

  public NotifyTransactionExpiredHandler(
      Sep24TransactionStore txn24Store,
      Sep31TransactionStore txn31Store,
      RequestValidator requestValidator,
      AssetService assetService) {
    super(
        txn24Store,
        txn31Store,
        requestValidator,
        assetService,
        NotifyTransactionExpiredRequest.class);
  }

  @Override
  public ActionMethod getActionType() {
    return NOTIFY_TRANSACTION_EXPIRED;
  }

  @Override
  protected SepTransactionStatus getNextStatus(
      JdbcSepTransaction txn, NotifyTransactionExpiredRequest request) {
    return EXPIRED;
  }

  @Override
  protected Set<SepTransactionStatus> getSupportedStatuses(JdbcSepTransaction txn) {
    if (SEP_24 == Sep.from(txn.getProtocol())) {
      return Arrays.stream(SepTransactionStatus.values())
          .filter(s -> !isErrorStatus(s) && !isFinalStatus(s))
          .collect(toSet());
    }
    return Set.of();
  }

  @Override
  protected void updateTransactionWithAction(
      JdbcSepTransaction txn, NotifyTransactionExpiredRequest request) {}
}

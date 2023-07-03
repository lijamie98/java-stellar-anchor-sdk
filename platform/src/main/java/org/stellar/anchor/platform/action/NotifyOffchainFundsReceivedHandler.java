package org.stellar.anchor.platform.action;

import static org.stellar.anchor.api.platform.PlatformTransactionData.Kind.DEPOSIT;
import static org.stellar.anchor.api.rpc.action.ActionMethod.NOTIFY_OFFCHAIN_FUNDS_RECEIVED;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_ANCHOR;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_EXTERNAL;
import static org.stellar.anchor.api.sep.SepTransactionStatus.PENDING_USR_TRANSFER_START;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import javax.validation.Validator;
import org.springframework.stereotype.Service;
import org.stellar.anchor.api.exception.AnchorException;
import org.stellar.anchor.api.exception.BadRequestException;
import org.stellar.anchor.api.platform.PlatformTransactionData.Kind;
import org.stellar.anchor.api.rpc.action.ActionMethod;
import org.stellar.anchor.api.rpc.action.NotifyOffchainFundsReceivedRequest;
import org.stellar.anchor.api.sep.SepTransactionStatus;
import org.stellar.anchor.asset.AssetService;
import org.stellar.anchor.config.CustodyConfig;
import org.stellar.anchor.custody.CustodyService;
import org.stellar.anchor.platform.data.JdbcSep24Transaction;
import org.stellar.anchor.platform.data.JdbcSepTransaction;
import org.stellar.anchor.sep24.Sep24TransactionStore;
import org.stellar.anchor.sep31.Sep31TransactionStore;

@Service
public class NotifyOffchainFundsReceivedHandler
    extends ActionHandler<NotifyOffchainFundsReceivedRequest> {

  private final CustodyService custodyService;
  private final CustodyConfig custodyConfig;

  public NotifyOffchainFundsReceivedHandler(
      Sep24TransactionStore txn24Store,
      Sep31TransactionStore txn31Store,
      Validator validator,
      AssetService assetService,
      CustodyService custodyService,
      CustodyConfig custodyConfig) {
    super(txn24Store, txn31Store, validator, assetService);
    this.custodyService = custodyService;
    this.custodyConfig = custodyConfig;
  }

  @Override
  public ActionMethod getActionType() {
    return NOTIFY_OFFCHAIN_FUNDS_RECEIVED;
  }

  @Override
  protected SepTransactionStatus getNextStatus(
      JdbcSepTransaction txn, NotifyOffchainFundsReceivedRequest request) {
    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    if (DEPOSIT == Kind.from(txn24.getKind())) {
      return PENDING_ANCHOR;
    }
    throw new IllegalArgumentException(
        String.format(
            "Invalid kind[%s] for protocol[%s] and action[%s]",
            txn24.getKind(), txn24.getProtocol(), getActionType()));
  }

  @Override
  protected Set<SepTransactionStatus> getSupportedStatuses(JdbcSepTransaction txn) {
    Set<SepTransactionStatus> supportedStatuses = new HashSet<>();
    JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
    if (DEPOSIT == Kind.from(txn24.getKind())) {
      supportedStatuses.add(PENDING_USR_TRANSFER_START);
      if (txn.getTransferReceivedAt() == null) {
        supportedStatuses.add(PENDING_EXTERNAL);
      }
    }
    return supportedStatuses;
  }

  @Override
  protected Set<String> getSupportedProtocols() {
    return Set.of("24");
  }

  @Override
  protected void updateTransactionWithAction(
      JdbcSepTransaction txn, NotifyOffchainFundsReceivedRequest request) throws AnchorException {
    if (request.getExternalTransactionId() != null) {
      txn.setExternalTransactionId(request.getExternalTransactionId());
      if (request.getFundsReceivedAt() != null) {
        txn.setTransferReceivedAt(request.getFundsReceivedAt());
      } else {
        txn.setTransferReceivedAt(Instant.now());
      }
    }

    if (!(request.getAmountIn() == null
            && request.getAmountOut() == null
            && request.getAmountFee() == null)
        || !(request.getAmountIn() != null
            && request.getAmountOut() != null
            && request.getAmountFee() != null)) {
      throw new BadRequestException(
          "At least one of amount_in, amount_out and amount_fee is not set");
    }

    validateAsset("amount_in", request.getAmountIn());
    validateAsset("amount_out", request.getAmountOut());
    validateAsset("amount_fee", request.getAmountFee(), true);

    if (request.getAmountIn() != null) {
      txn.setAmountIn(request.getAmountIn().getAmount());
      txn.setAmountInAsset(request.getAmountIn().getAsset());
    }
    if (request.getAmountOut() != null) {
      txn.setAmountOut(request.getAmountOut().getAmount());
      txn.setAmountOutAsset(request.getAmountOut().getAsset());
    }
    if (request.getAmountFee() != null) {
      txn.setAmountFee(request.getAmountFee().getAmount());
      txn.setAmountFeeAsset(request.getAmountFee().getAsset());
    }

    if (custodyConfig.isCustodyIntegrationEnabled() && "24".equals(txn.getProtocol())) {
      JdbcSep24Transaction txn24 = (JdbcSep24Transaction) txn;
      if (DEPOSIT == Kind.from(txn24.getKind())) {
        custodyService.createTransaction(txn24);
      }
    }
  }
}

package com.tanggo.fund.eth.lib.inbound.controller;

import com.tanggo.fund.eth.lib.service.EthereumApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 以太坊 JSON-RPC HTTP 控制器
 * 作为 Inbound Adapter，处理 HTTP JSON-RPC 请求并调用应用服务层
 *
 * 访问地址: POST http://localhost:8545/eth/rpc
 *
 * 请求示例:
 * {
 *   "jsonrpc": "2.0",
 *   "method": "eth_getBalance",
 *   "params": ["0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb", "latest"],
 *   "id": 1
 * }
 */
@RestController
@RequestMapping("/eth/rpc")
public class EthereumRpcController {

    private final EthereumApplicationService applicationService;

    @Autowired
    public EthereumRpcController(EthereumApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 处理所有 JSON-RPC 请求
     */
    @PostMapping
    public Map<String, Object> handleJsonRpc(@RequestBody Map<String, Object> request) {
        String method = (String) request.get("method");
        List<Object> params = (List<Object>) request.get("params");
        Object id = request.get("id");

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            Object result = dispatchMethod(method, params);
            response.put("result", result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", -32603);
            error.put("message", "Internal error: " + e.getMessage());
            response.put("error", error);
        }

        return response;
    }

    /**
     * 分发 JSON-RPC 方法调用到应用服务层
     */
    @SuppressWarnings("unchecked")
    private Object dispatchMethod(String method, List<Object> params) {
        return switch (method) {
            case "eth_getBalance" -> {
                BigInteger balance = applicationService.getBalance(
                        (String) params.get(0),
                        (String) params.get(1)
                );
                yield toHex(balance);
            }
            case "eth_blockNumber" -> {
                long blockNumber = applicationService.getCurrentBlockNumber();
                yield toHex(blockNumber);
            }
            case "eth_sendRawTransaction" -> applicationService.sendRawTransaction(
                    (String) params.get(0)
            );
            case "eth_getTransactionByHash" -> applicationService.getTransactionByHash(
                    (String) params.get(0)
            );
            case "eth_getTransactionReceipt" -> applicationService.getTransactionReceipt(
                    (String) params.get(0)
            );
            case "eth_getBlockByNumber" -> applicationService.getBlockByNumber(
                    (String) params.get(0),
                    (Boolean) params.get(1)
            );
            case "eth_getBlockByHash" -> applicationService.getBlockByHash(
                    (String) params.get(0),
                    (Boolean) params.get(1)
            );
            case "eth_getTransactionCount" -> {
                long count = applicationService.getTransactionCount(
                        (String) params.get(0),
                        (String) params.get(1)
                );
                yield toHex(count);
            }
            case "eth_call" -> applicationService.callContract(
                    (Map<String, String>) params.get(0),
                    (String) params.get(1)
            );
            case "eth_estimateGas" -> {
                BigInteger gasEstimate = applicationService.estimateGas(
                        (Map<String, String>) params.get(0)
                );
                yield toHex(gasEstimate);
            }
            case "eth_gasPrice" -> {
                BigInteger gasPrice = applicationService.getGasPrice();
                yield toHex(gasPrice);
            }
            case "eth_getLogs" -> applicationService.getLogs(
                    (Map<String, Object>) params.get(0)
            );
            case "eth_chainId" -> {
                long chainId = applicationService.getChainId();
                yield toHex(chainId);
            }
            case "web3_clientVersion" -> applicationService.getClientVersion();
            case "net_version" -> applicationService.getNetworkId();
            case "eth_accounts" -> applicationService.getAccounts();
            case "personal_newAccount" -> applicationService.createAccount(
                    (String) params.get(0)
            );
            case "personal_unlockAccount" -> applicationService.unlockAccount(
                    (String) params.get(0),
                    (String) params.get(1),
                    (Integer) params.get(2)
            );
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    // ==================== 工具方法 ====================

    /**
     * 将 BigInteger 转换为十六进制字符串（带 0x 前缀）
     */
    private String toHex(BigInteger value) {
        if (value == null) {
            return "0x0";
        }
        return "0x" + value.toString(16);
    }

    /**
     * 将 long 转换为十六进制字符串（带 0x 前缀）
     */
    private String toHex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
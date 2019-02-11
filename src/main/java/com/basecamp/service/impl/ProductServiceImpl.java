package com.basecamp.service.impl;

import com.basecamp.exception.InternalException;
import com.basecamp.exception.InvalidDataException;
import com.basecamp.service.ProductService;
import com.basecamp.wire.GetHandleProductIdsResponse;
import com.basecamp.wire.GetProductInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class ProductServiceImpl implements ProductService {

    private final ConcurrentTaskService taskService;
    private final int BUGS_RACERS = 5;
    private Map<String, Long> raceResults = new ConcurrentHashMap<>(BUGS_RACERS);

    public GetProductInfoResponse getProductInfo(String productId) {

        validateId(productId);

        log.info("Product id {} was successfully validated.", productId);

        return callToDbAnotherServiceETC(productId);
    }

    public GetHandleProductIdsResponse handleProducts(List<String> productIds) {
        Map<String, Future<String>> handledTasks = new HashMap<>();
        productIds.forEach(productId ->
                handledTasks.put(
                        productId,
                        taskService.handleProductIdByExecutor(productId)));

        List<String> handledIds = handledTasks.entrySet().stream().map(stringFutureEntry -> {
            try {
                return stringFutureEntry.getValue().get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error(stringFutureEntry.getKey() + " execution error!");
            }

            return stringFutureEntry.getKey() + " is not handled!";
        }).collect(Collectors.toList());

        return GetHandleProductIdsResponse.builder()
                .productIds(handledIds)
                .build();
    }

    public void stopProductExecutor() {
        log.warn("Calling to stop product executor...");

        taskService.stopExecutorService();

        log.info("Product executor stopped.");
    }

    @Override
    public void homework() {
        CountDownLatch start_latch = new CountDownLatch(BUGS_RACERS);
        CountDownLatch finish_latch = new CountDownLatch(BUGS_RACERS);
        for (int i = 1; i <= BUGS_RACERS; i++) {
            new BugsRacer(start_latch, finish_latch, "BugRacer #" + i).start();
            String msg = String.format("BugRacer %d was created ", i);
            log.info(msg);
        }
        try {
            finish_latch.await(); //waiting for others to finish
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        raceResults.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(System.out::println);
    }

    private void validateId(String id) {

        if (StringUtils.isEmpty(id)) {
            // all messages could be moved to messages properties file (resources)
            String msg = "ProductId is not set.";
            log.error(msg);
            throw new InvalidDataException(msg);
        }

        try {
            Integer.valueOf(id);
        } catch (NumberFormatException e) {
            String msg = String.format("ProductId %s is not a number.", id);
            log.error(msg);
            throw new InvalidDataException(msg);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new InternalException(e.getMessage());
        }
    }

    private GetProductInfoResponse callToDbAnotherServiceETC(String productId) {
        return GetProductInfoResponse.builder()
                .id(productId)
                .name("ProductName")
                .status("ProductStatus")
                .build();
    }

    class BugsRacer extends Thread {
        private CountDownLatch countDownLatch;
        private CountDownLatch finish_latch;
        private String bugRacer_name;
        private int position;
        private int track_length = 20;
        private int randomStep = ThreadLocalRandom.current().nextInt(1, 5);

        BugsRacer(CountDownLatch countDownLatch, CountDownLatch finish_latch, String bugRacer_name) {
            this.countDownLatch = countDownLatch;
            this.bugRacer_name = bugRacer_name;
            this.finish_latch = finish_latch;
        }

        @Override
        public void run() {
            String msg = String.format("%s is at the starting position", bugRacer_name);
            log.info(msg);
            countDownLatch.countDown(); // bugsRacer is at start

            try {
                countDownLatch.await(); //waiting for others to start
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long start_time = System.nanoTime();
            while (position < track_length) {
                position += randomStep;
                String format_msg = String.format("%s is at %d, step is %d", bugRacer_name, position, randomStep);
                log.info(format_msg);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            long race_duration = (System.nanoTime() - start_time);
            raceResults.put(bugRacer_name, TimeUnit.MICROSECONDS.convert(race_duration, TimeUnit.NANOSECONDS));
            finish_latch.countDown();
        }
    }
}


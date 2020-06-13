package com.dm.controller;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.RedissonRedLock;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ,;,,;
 * ,;;'(
 * __      ,;;' ' \
 * /'  '\'~~'~' \ /'\.)
 * ,;(      )    /  |.
 * ,;' \    /-.,,(   ) \
 * ) /       ) / )|
 * ||        ||  \)
 * (_\       (_\
 *
 * @ClassName REedisLockController
 * @Description TODO
 * @Author dm
 * @Date 2020/6/13 11:09
 * @slogan: 我自横刀向天笑，笑完我就去睡觉
 * @Version 1.0
 **/
@Slf4j
@RestController
public class RedisLockController {

    @Autowired
    private Redisson redisson;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RequestMapping("/deduct_stock")
    public String deductStock() throws InterruptedException {
        String lock_key = "stock_lock";
//        String clientId = UUID.randomUUID().toString();
        RLock redissonLock = redisson.getLock(lock_key);
        try {
            // 1,这里加上超时时间，主要怕中间执行业务代码的时候，服务器宕机了。造成锁没有释放。
            // 2,这时候有一个问题，如果一个业务代码要执行13秒，而锁失效时间是10秒，意味着业务在执行到第10秒的时候
            // 这把锁被释放了，这时候第二个线程进来加锁了，然后第一个线程执行到第13秒的时候，第二个线程的锁被
            // 第一个线程释放了，这样导致了后续的锁全部出现问题，造成了所失效的问题。这个问题出现的原因是释放
            // 锁的线程不是加锁的线程,我们只要控制释放锁的线程一定是加锁的线程就可以了，我们可以对锁的value做手脚
//            Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lock_key, clientId,10,TimeUnit.SECONDS);
            // 4,如果一个业务代码就是会执行时间超过10秒，那么我们这把锁其实形同虚设，每次都会有多个进程进来执行我们的代码
            // 也是有可能出现bug的。这时候当我们应该要做一个锁续命
            // 锁续命原理：当我们添加了一把锁时候，后台会同时开启一个定时任务，每隔几秒会查看一下锁是不是存在，如果锁存在
            // 说明业务代码没有执行完成，后台会把锁重新置为10秒，如果锁不存在，直接将定时任务kill掉
//            if (!lock) {
//                return "error";
//            }
            redissonLock.lock();

//        synchronized (this) {
            int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("stock"));
            if (stock > 0) {
                int realStock = stock - 1;
                stringRedisTemplate.opsForValue().set("stock", realStock + "");
                System.out.println("扣减成功，剩余库存:" + realStock + "");
            } else {
                System.out.println("扣减失败，库存不足");
            }
//        }
            // 3,這裡必须加上try/finally,必须保证stringRedisTemplate.delete(lock_key)这段代码必须执行
            // 主要防止中间代码抛异常导致锁没有释放掉，造成死锁
        } finally {
            redissonLock.unlock();
//            if (stringRedisTemplate.opsForValue().get(lock_key).equals(clientId)) {
//                stringRedisTemplate.delete(lock_key);
//            }
        }

        return null;
    }

    @RequestMapping("redlock")
    public String redlock() throws Exception {
        String lockKey = "stock_lock";
        //这里需要自己实例化不同redis实例的redisson客户端连接，这里只是伪代码用一个redisson客户端简化了
        RLock lock1 = redisson.getLock(lockKey);
        RLock lock2 = redisson.getLock(lockKey);
        RLock lock3 = redisson.getLock(lockKey);

        /**
         * 根据多个 RLock 对象构建 RedissonRedLock （最核心的差别就在这里）
         */
        RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);
        try {
            /**
             * 4.尝试获取锁
             * waitTimeout 尝试获取锁的最大等待时间，超过这个值，则认为获取锁失败
             * leaseTime   锁的持有时间,超过这个时间锁会自动失效（值应设置为大于业务处理的时间，确保在锁有效期内业务能处理完）
             */
            boolean res = redLock.tryLock(10, 30, TimeUnit.SECONDS);
            if (res) {
                //成功获得锁，在这里处理业务
            }
        } catch (Exception e) {
            throw new RuntimeException("lock fail");
        } finally {
            //无论如何, 最后都要解锁
            redLock.unlock();
        }
        return null;
    }

}

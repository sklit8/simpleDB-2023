# 6.830 Lab 6: Rollback and Recovery

**Assigned: Monday, May 3, 2023**
**Due: Wednesday, May 19, 202311:59 PM EST**

## 1. Introduction

在这个实验室中，你将实现基于日志的**中止回滚和**基于日志的**崩溃恢复**。我们为你提供了定义日志格式的代码，并在交易过程中的适当时候将记录附加到日志文件中。你将使用日志文件的内容实现回滚和恢复。

我们提供的日志代码产生的记录是用于物理的全页**撤销和重做**。当一个页面第一次被读入时，我们的代码会记住该页面的**原始内容**，作为一个之前的图像。当一个事务更新一个页面时，相应的日志记录包含记忆中的before-image以及修改后的页面内容作为after-image。你将使用before-image在中止过程中回滚，并在恢复过程中撤销失败的事务，而after-image则在恢复过程中重做胜利者。

我们能够摆脱整个页面的物理UNDO（而ARIES必须做逻辑UNDO），因为我们在做页级锁，而且我们没有索引，在UNDO时的结构可能与最初写日志时不同。页级锁简化了事情的原因是，如果一个事务修改了一个页面，它一定有一个独占锁，这意味着没有其他事务在同时修改它，所以我们可以通过覆盖整个页面来UNDO对它的修改。

你的BufferPool已经通过删除脏页实现了中止，并通过在提交时强制脏页到磁盘来假装实现原子提交。日志允许更灵活的缓冲区管理（STEAL和NO-FORCE），我们的测试代码在某些地方调用BufferPool.flushAllPages()，以行使这种灵活性。

## 2. Rollback

阅读LogFile.java中的注释，了解日志文件格式的描述。你应该在LogFile.java中看到一组函数，比如logCommit()，它们生成每一种日志记录并将其追加到日志中。

你的第一项工作是实现LogFile.java中的rollback()函数。当一个事务中止时，在该事务释放其锁之前，这个函数被调用。它的工作是解除事务可能对数据库做出的任何改变。

你的回滚()应该读取日志文件，找到所有与中止事务相关的更新记录，从每条记录中提取前像，并将前像写入表文件。使用raf.seek()在日志文件中移动，使用raf.readInt()等来检查它。使用readPageData()来读取每个前后的图像。你可以使用map tidToFirstLogRecord（它从交易ID映射到堆文件中的偏移量）来确定从哪里开始读取特定交易的日志文件。你需要确保从缓冲池中丢弃任何你写回表文件的前图像的页面。

当你开发你的代码时，你可能会发现Logfile.print()方法对于显示日志的当前内容很有用。

------

**Exercise 1: LogFile.rollback()**

Implement LogFile.rollback().

After completing this exercise, you should be able to pass the TestAbort and TestAbortCommitInterleaved sub-tests of the LogTest system test.

## 3. Recovery

如果数据库崩溃，然后重新启动，LogFile.recover()将在任何新事务开始之前被调用。你的实现应该：

1. 读取最后一个检查点，如果有的话。

2. 从检查点向前扫描（如果没有检查点，则从日志文件的开始），以建立失败的事务集。在这个过程中重做更新。你可以安全地在检查点开始重做，因为LogFile.logCheckpoint()会将所有的脏缓冲区刷到磁盘上。
3. 取消对失败事务的更新。

------

**Exercise 2: LogFile.recover()**

Implement LogFile.recover().

After completing this exercise, you should be able to pass all of the LogTest system test.

------


package com.oj.backend.service;

import com.oj.backend.common.PageResult;
import com.oj.backend.dto.ChangelogCreateDTO;
import com.oj.backend.dto.ChangelogQueryDTO;
import com.oj.backend.dto.ChangelogUpdateDTO;
import com.oj.backend.vo.ChangelogLatestVO;
import com.oj.backend.vo.ChangelogVO;

public interface ChangelogService {

    PageResult<ChangelogVO> listChangelogs(ChangelogQueryDTO queryDTO);

    ChangelogLatestVO getLatestChangelog();

    ChangelogVO createChangelog(ChangelogCreateDTO dto);

    ChangelogVO updateChangelog(Long id, ChangelogUpdateDTO dto);

    void deleteChangelog(Long id);
}
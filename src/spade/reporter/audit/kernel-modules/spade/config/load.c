/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */

#include "spade/config/load.h"
#include <linux/fs.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>

#define MAX_LINE_LENGTH 256
#define MAX_FILE_SIZE 4096

/**
 * Helper function to trim whitespace from a string
 */
static void trim_whitespace(char *str)
{
    char *end;

    // Trim leading whitespace
    while (*str == ' ' || *str == '\t' || *str == '\n' || *str == '\r')
        str++;

    if (*str == 0)
        return;

    // Trim trailing whitespace
    end = str + strlen(str) - 1;
    while (end > str && (*end == ' ' || *end == '\t' || *end == '\n' || *end == '\r'))
        end--;

    *(end + 1) = '\0';
}

/**
 * Helper function to compare strings case-insensitively
 */
static int strcasecmp_simple(const char *s1, const char *s2)
{
    while (*s1 && *s2)
    {
        char c1 = (*s1 >= 'A' && *s1 <= 'Z') ? *s1 + 32 : *s1;
        char c2 = (*s2 >= 'A' && *s2 <= 'Z') ? *s2 + 32 : *s2;
        if (c1 != c2)
            return c1 - c2;
        s1++;
        s2++;
    }
    return *s1 - *s2;
}

int config_parse_line(const char *line, struct config *config)
{
    char *key, *value;
    char *line_copy;
    char *equals_pos;

    if (!line || !config)
        return -EINVAL;

    // Allocate memory for line copy
    line_copy = kstrdup(line, GFP_KERNEL);
    if (!line_copy)
        return -ENOMEM;

    // Skip comments and empty lines
    trim_whitespace(line_copy);
    if (line_copy[0] == '#' || line_copy[0] == '\0')
    {
        kfree(line_copy);
        return 0;
    }

    // Find the equals sign
    equals_pos = strchr(line_copy, '=');
    if (!equals_pos)
    {
        kfree(line_copy);
        return -EINVAL;
    }

    // Split into key and value
    *equals_pos = '\0';
    key = line_copy;
    value = equals_pos + 1;

    trim_whitespace(key);
    trim_whitespace(value);

    // Parse debug option
    if (strcasecmp_simple(key, "debug") == 0)
    {
        if (strcasecmp_simple(value, "true") == 0 || strcmp(value, "1") == 0)
        {
            config->debug = true;
        }
        else if (strcasecmp_simple(value, "false") == 0 || strcmp(value, "0") == 0)
        {
            config->debug = false;
        }
        else
        {
            kfree(line_copy);
            return -EINVAL;
        }
    }
    // Parse sys_hook_type option
    else if (strcasecmp_simple(key, "sys_hook_type") == 0)
    {
        if (strcasecmp_simple(value, "ftrace") == 0)
        {
            config->sys_hook_type = CONFIG_SYSCALL_HOOK_FTRACE;
        }
        else
        {
            kfree(line_copy);
            return -EINVAL;
        }
    }
    // Unknown option - ignore or return error
    else
    {
        pr_warn("Unknown config option: %s\n", key);
    }

    kfree(line_copy);
    return 0;
}

int config_load_from_file(const char *filepath, struct config *config)
{
    struct file *file;
    char *buffer;
    char *line_start, *line_end;
    loff_t pos = 0;
    ssize_t bytes_read;
    int ret = 0;

    if (!filepath || !config)
        return -EINVAL;

    // Open the file
    file = filp_open(filepath, O_RDONLY, 0);
    if (IS_ERR(file))
    {
        pr_err("Failed to open config file: %s\n", filepath);
        return PTR_ERR(file);
    }

    // Allocate buffer for file contents
    buffer = kmalloc(MAX_FILE_SIZE, GFP_KERNEL);
    if (!buffer)
    {
        filp_close(file, NULL);
        return -ENOMEM;
    }

    // Read the file
    bytes_read = kernel_read(file, buffer, MAX_FILE_SIZE - 1, &pos);
    if (bytes_read < 0)
    {
        pr_err("Failed to read config file: %s\n", filepath);
        ret = bytes_read;
        goto cleanup;
    }

    // Null-terminate the buffer
    buffer[bytes_read] = '\0';

    // Parse line by line
    line_start = buffer;
    while (line_start < buffer + bytes_read)
    {
        // Find end of line
        line_end = strchr(line_start, '\n');
        if (line_end)
        {
            *line_end = '\0';
        }

        // Parse the line
        ret = config_parse_line(line_start, config);
        if (ret < 0)
        {
            pr_err("Failed to parse config line: %s\n", line_start);
            goto cleanup;
        }

        // Move to next line
        if (line_end)
        {
            line_start = line_end + 1;
        }
        else
        {
            break;
        }
    }

    pr_info("Successfully loaded configuration from %s\n", filepath);
    pr_info("  debug: %s\n", config->debug ? "true" : "false");
    pr_info("  sys_hook_type: %s\n",
            config->sys_hook_type == CONFIG_SYSCALL_HOOK_FTRACE ? "ftrace" : "unknown");

cleanup:
    kfree(buffer);
    filp_close(file, NULL);
    return ret;
}
